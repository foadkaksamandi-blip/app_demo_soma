package com.soma.merchant.ble

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.soma.merchant.R
import com.soma.merchant.data.WalletType
import shared.utils.DateUtils
import java.nio.charset.Charset
import java.util.*

/**
 * BLE Peripheral GATT Server برای اپ فروشنده
 * - Service UUID: 0000feed-0000-1000-8000-00805f9b34fb
 * - RX  (Write from buyer): 0000fee1-0000-1000-8000-00805f9b34fb
 * - TX  (Notify to buyer):  0000fee2-0000-1000-8000-00805f9b34fb
 */
class BlePeripheralService : Service() {

    companion object {
        private const val TAG = "SOMA-MERCHANT-BLE"
        private const val NOTI_CHANNEL = "soma_ble_channel"
        private const val NOTI_ID = 2210

        val SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
        val CHAR_RX_UUID: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb") // Buyer -> Merchant (WRITE)
        val CHAR_TX_UUID: UUID = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb") // Merchant -> Buyer (NOTIFY)
    }

    // Android BLE
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    // Characteristics
    private lateinit var charRx: BluetoothGattCharacteristic
    private lateinit var charTx: BluetoothGattCharacteristic

    // وضعیت
    private var lastSubscribedDevice: BluetoothDevice? = null

    // دادهٔ پرداخت در صف (از Activity ست می‌شود)
    data class PendingPayment(
        val amount: Long,
        val wallet: WalletType,
        val txId: String,
        val ts: String = DateUtils.nowJalaliDateTime()
    )
    @Volatile private var pending: PendingPayment? = null

    // ------------ Binder برای ارتباط Activity با Service -------------
    inner class LocalBinder : Binder() {
        fun startServer() = startGattServer()
        fun stopServer() = stopGattServer()
        fun advertise(enable: Boolean) = toggleAdvertise(enable)
        fun setPayment(p: PendingPayment) { pending = p; notifyIfReady() }
        fun isRunning(): Boolean = gattServer != null
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    // ---------------- Service lifecycle ----------------
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        createNotiChannel()
        startForeground(
            NOTI_ID,
            NotificationCompat.Builder(this, NOTI_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("سرویس بلوتوث فروشنده فعال است")
                .setOngoing(true)
                .build()
        )
        Log.i(TAG, "Service created")
    }

    override fun onDestroy() {
        stopGattServer()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    // ---------------- GATT Server ----------------
    private fun startGattServer() {
        if (gattServer != null) return
        val mgr = bluetoothManager ?: return
        gattServer = mgr.openGattServer(this, gattCallback)

        // Service + Characteristics
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        charRx = BluetoothGattCharacteristic(
            CHAR_RX_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        charTx = BluetoothGattCharacteristic(
            CHAR_TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(charRx)
        service.addCharacteristic(charTx)

        gattServer?.addService(service)
        Log.i(TAG, "GATT server started")
    }

    private fun stopGattServer() {
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        Log.i(TAG, "GATT server stopped")
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.i(TAG, "Conn state: $device -> $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                lastSubscribedDevice = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (lastSubscribedDevice?.address == device.address) lastSubscribedDevice = null
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // خریدار برای تأیید/درخواست می‌نویسد؛
            if (characteristic.uuid == CHAR_RX_UUID) {
                val rx = try { value.toString(Charset.forName("UTF-8")) } catch (_: Exception) { "" }
                Log.i(TAG, "RX from buyer: $rx")
                // بلافاصله اگر پرداخت در صف داریم، پاسخ Notify بده
                notifyIfReady(device)
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            } else {
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            // اشتراک Notify (Client Characteristic Configuration Descriptor)
            lastSubscribedDevice = device
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            notifyIfReady(device)
        }
    }

    // ---------------- Advertising ----------------
    private fun toggleAdvertise(enable: Boolean) {
        val adv = advertiser ?: return
        if (enable) {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            adv.startAdvertising(settings, data, object : AdvertiseCallback() {})
            Log.i(TAG, "Advertising started")
        } else {
            adv.stopAdvertising(object : AdvertiseCallback() {})
            Log.i(TAG, "Advertising stopped")
        }
    }

    // ---------------- Notify helper ----------------
    private fun notifyIfReady(target: BluetoothDevice? = lastSubscribedDevice) {
        val server = gattServer ?: return
        val device = target ?: return
        val p = pending ?: return

        // پی‌لود سادهٔ JSON آفلاین
        val payload = """{
  "txId":"${p.txId}",
  "amount":${p.amount},
  "wallet":"${p.wallet}",
  "ts":"${p.ts}"
}""".trimIndent()

        charTx.value = payload.toByteArray(Charset.forName("UTF-8"))
        val ok = server.notifyCharacteristicChanged(device, charTx, /* confirm */ false)
        Log.i(TAG, "Notify sent ($ok): $payload")
        if (ok) pending = null
    }

    // --------------- Foreground Noti ---------------
    private fun createNotiChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTI_CHANNEL, "SOMA BLE", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
}
