package com.soma.merchant.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import com.soma.merchant.R
import com.soma.merchant.util.ReplayProtector
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.UUID

/**
 * BLE Peripheral: پذیرش مبلغ از خریدار از طریق characteristic قابل نوشتن
 * بدون تغییر UI/QR موجود. فقط سرویس پس‌زمینه.
 */
class BlePeripheralService : Service() {

    companion object {
        // شناسه‌ها ثابت بماند تا کلاینت پیداکند
        val SERVICE_UUID: UUID = UUID.fromString("5e6d1a70-9f59-4d7e-9a2b-ff7b20a90a10")
        val CHAR_RX_WRITE_UUID: UUID = UUID.fromString("a3af3c56-2a2a-47cc-87a8-2f7d0f87b001") // دریافت مبلغ از خریدار
        val CHAR_TX_NOTIFY_UUID: UUID = UUID.fromString("b7c2d6aa-2cf2-4b8f-9d0d-7c0f8b87b002") // ارسال رسید به خریدار

        const val CHANNEL_ID = "soma_ble_channel"
        const val NOTIF_ID = 101
        const val ACTION_INCOMING_PAYMENT = "com.soma.merchant.ACTION_INCOMING_PAYMENT"
        const val EXTRA_AMOUNT = "EXTRA_AMOUNT"
        const val EXTRA_WALLET = "EXTRA_WALLET"
    }

    private var gattServer: BluetoothGattServer? = null
    private var txNotifyChar: BluetoothGattCharacteristic? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        startForeground(NOTIF_ID, createNotification())
        startGattServer()
        startAdvertising()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAdvertising()
        gattServer?.close()
        super.onDestroy()
    }

    // ---------- Notification ----------
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "SOMA BLE", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SOMA Merchant BLE")
            .setContentText("در حال پذیرش پرداخت آفلاین (Bluetooth LE)")
            .setOngoing(true)
            .build()
    }

    // ---------- GATT Server ----------
    private fun startGattServer() {
        val server = bluetoothManager?.openGattServer(this, gattServerCallback) ?: return
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val rxWrite = BluetoothGattCharacteristic(
            CHAR_RX_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val txNotify = BluetoothGattCharacteristic(
            CHAR_TX_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        txNotifyChar = txNotify

        service.addCharacteristic(rxWrite)
        service.addCharacteristic(txNotify)
        server.addService(service)

        gattServer = server
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic?.uuid == CHAR_RX_WRITE_UUID && value != null) {
                val json = JSONObject(String(value, Charset.forName("UTF-8")))
                val amount = json.optLong("amount", 0L)
                val wallet = json.optString("wallet", "اصلی")
                val nonce  = json.optString("nonce", "")
                val ts     = json.optLong("ts", 0L)

                // ضد-تکرار ساده
                val ok = ReplayProtector.acceptOnce(nonce)
                // قوانین پایه
                val valid = ok && amount > 0 && ts > 0

                // پاسخ به کلاینت (رسید ساده)
                val ack = JSONObject()
                    .put("ok", valid)
                    .put("msg", if (valid) "ACCEPTED" else "REJECTED")
                    .put("ts", System.currentTimeMillis())
                    .toString().toByteArray(Charset.forName("UTF-8"))

                // نوتیفای
                txNotifyChar?.value = ack
                device?.let {
                    bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)?.forEach { dev ->
                        gattServer?.notifyCharacteristicChanged(dev, txNotifyChar, false)
                    }
                }

                // پاسخ به درخواست write
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)

                if (valid) {
                    // Broadcast به Activity برای ثبت تاریخچه/موجودی
                    val intent = Intent(ACTION_INCOMING_PAYMENT)
                        .putExtra(EXTRA_AMOUNT, amount.toString())
                        .putExtra(EXTRA_WALLET, wallet)
                    sendBroadcast(intent)
                }
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    // ---------- Advertising ----------
    private var advCallback: AdvertiseCallback? = null

    private fun startAdvertising() {
        val adapter = bluetoothAdapter ?: return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advCallback = object : AdvertiseCallback() { }
        advertiser.startAdvertising(settings, data, advCallback)
    }

    private fun stopAdvertising() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.let { adv ->
            advCallback?.let { adv.stopAdvertising(it) }
        }
    }
}
