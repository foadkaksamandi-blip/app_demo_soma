package com.soma.merchant.ble

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.soma.merchant.R
import java.util.UUID

/**
 * سرور BLE ساده برای دمو: یک سرویس با یک characteristic برای “پرداخت”.
 * - شروع/توقف advertise
 * - دریافت مقدار از خریدار (write)
 * - ارسال تأیید (notify)
 */
class BlePeripheralService : Service() {

    companion object {
        // UUIDهای دمو (دلخواه ولی ثابت)
        val SERVICE_UUID: UUID = UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb")
        val CHAR_PAYMENT_UUID: UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb")
        val DESC_CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val ACTION_INCOMING_PAYMENT = "com.soma.merchant.ble.ACTION_INCOMING_PAYMENT"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_WALLET = "wallet"
    }

    private val tag = "BlePeripheralService"

    private lateinit var bluetoothManager: BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private var currentCentral: android.bluetooth.BluetoothDevice? = null

    // characteristic قابل‌نوشتن + notify
    private lateinit var paymentCharacteristic: BluetoothGattCharacteristic

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun service(): BlePeripheralService = this@BlePeripheralService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        advertiser = adapter.bluetoothLeAdvertiser

        gattServer = bluetoothManager.openGattServer(this, gattCallback)
        setupGattDatabase()

        startAdvertising()
        Log.d(tag, "BLE Peripheral created & advertising started")
    }

    override fun onDestroy() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        super.onDestroy()
    }

    /** ایجاد سرویس و characteristic‌ها */
    private fun setupGattDatabase() {
        val service = android.bluetooth.BluetoothGattService(
            SERVICE_UUID,
            android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        paymentCharacteristic = BluetoothGattCharacteristic(
            CHAR_PAYMENT_UUID,
            // Write از سمت خریدار + Notify از سمت فروشنده
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val ccc = BluetoothGattDescriptor(DESC_CCC_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        paymentCharacteristic.addDescriptor(ccc)

        service.addCharacteristic(paymentCharacteristic)
        gattServer?.addService(service)
    }

    /** شروع advertise */
    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /** توقف advertise */
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(tag, "Advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(tag, "Advertising failed: $errorCode")
        }
    }

    /** Callback سرور GATT */
    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: android.bluetooth.BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                currentCentral = device
                Log.d(tag, "Central connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                currentCentral = null
                Log.d(tag, "Central disconnected")
            }
        }

        override fun onDescriptorWriteRequest(
            device: android.bluetooth.BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            // فعال/غیرفعال کردن notify
            if (descriptor?.uuid == DESC_CCC_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: android.bluetooth.BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            // پرداخت جدید از خریدار رسید
            if (characteristic?.uuid == CHAR_PAYMENT_UUID && value != null) {
                val payload = try { String(value) } catch (_: Exception) { "" }
                Log.d(tag, "Incoming payment payload: $payload")

                // فرمت پیشنهادی payload: amount|wallet  (مثلاً: 100000|اصلی)
                val parts = payload.split("|")
                val amount = parts.getOrNull(0) ?: "0"
                val wallet = parts.getOrNull(1) ?: "اصلی"

                // اعلام به UI (MainActivity) برای ثبت تراکنش و نمایش
                Intent(ACTION_INCOMING_PAYMENT).also {
                    it.putExtra(EXTRA_AMOUNT, amount)
                    it.putExtra(EXTRA_WALLET, wallet)
                    sendBroadcast(it)
                }

                // جواب موفق به سنترال
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)

                // ارسال notify تأیید
                notifyPaymentAck("OK|$amount")
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    /** ارسال notify به دستگاه خریدار */
    private fun notifyPaymentAck(text: String) {
        try {
            paymentCharacteristic.value = text.toByteArray()
            currentCentral?.let { dev ->
                gattServer?.notifyCharacteristicChanged(
                    dev,
                    paymentCharacteristic,
                    false
                )
            }
        } catch (t: Throwable) {
            Log.e(tag, "notifyPaymentAck error", t)
        }
    }
}
