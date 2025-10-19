package com.soma.merchant.ble

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import com.soma.merchant.data.TxStore
import com.soma.merchant.data.WalletType
import com.soma.merchant.util.Crypto
import com.soma.merchant.util.ReplayProtector
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.*

class BlePeripheralService : Service() {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
        val RX_CHAR_UUID: UUID = UUID.fromString("0000aa01-0000-1000-8000-00805f9b34fb") // consumer writes payment
        val TX_CHAR_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb") // merchant notifies signed ack
        private const val NOTIF_ID = 777
        private const val NOTIF_CH = "soma_ble_peripheral"
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private lateinit var store: TxStore
    private lateinit var replay: ReplayProtector
    private lateinit var merchantId: String

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        store = TxStore(this)
        replay = ReplayProtector(this)
        merchantId = getSharedPreferences("merchant", MODE_PRIVATE)
            .getString("merchantId", "m-"+UUID.randomUUID().toString().take(8))!!
        createNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPeripheral()
        return START_STICKY
    }

    override fun onDestroy() {
        stopPeripheral()
        super.onDestroy()
    }

    fun startPeripheral() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        gattServer = bluetoothManager?.openGattServer(this, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val rx = BluetoothGattCharacteristic(
            RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        txCharacteristic = BluetoothGattCharacteristic(
            TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(rx)
        service.addCharacteristic(txCharacteristic)
        gattServer?.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true).build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID)).build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        startForeground(NOTIF_ID, buildNotification("BLE Advertising…"))
    }

    fun stopPeripheral() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        stopForeground(true)
        stopSelf()
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (characteristic?.uuid == RX_CHAR_UUID && value != null) {
                val msg = String(value, Charset.forName("UTF-8"))
                try {
                    val j = JSONObject(msg)
                    if (j.optString("type") == "payment") {
                        val walletStr = j.optString("walletType", "MAIN")
                        val wallet = runCatching { WalletType.valueOf(walletStr) }.getOrElse { WalletType.MAIN }
                        val amt = j.optLong("amount")
                        val txId = j.optString("txId")
                        val fresh = replay.isFreshAndMark("ble_$txId", System.currentTimeMillis())

                        if (fresh && amt > 0) {
                            val cur = store.balance(wallet)
                            store.setBalance(wallet, cur + amt)
                            store.add(amt, "دریافت BLE", wallet)
                        }

                        val ts = System.currentTimeMillis()
                        val canonical = "${merchantId}|${wallet.name}|${amt}|${txId}|${ts}"
                        val sig = Crypto.sign(canonical)
                        val ack = JSONObject()
                            .put("type", "merchant_ack_signed")
                            .put("merchantId", merchantId)
                            .put("walletType", wallet.name)
                            .put("amount", amt)
                            .put("txId", txId)
                            .put("ts", ts)
                            .put("sig", sig)

                        txCharacteristic?.value = ack.toString().toByteArray(Charset.forName("UTF-8"))
                        gattServer?.notifyCharacteristicChanged(device, txCharacteristic, false)
                    }
                } catch (_: Exception) { /* ignore */ }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            startForeground(NOTIF_ID, buildNotification("Advertise failed: $errorCode"))
        }
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            startForeground(NOTIF_ID, buildNotification("BLE Ready (connectable)"))
        }
    }

    private fun createNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CH, "SOMA BLE", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CH)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("SOMA Merchant")
            .setContentText(text)
            .setOngoing(true)
            .build()
}
