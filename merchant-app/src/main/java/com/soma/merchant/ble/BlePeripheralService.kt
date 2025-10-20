package com.soma.merchant.ble

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log

import java.util.UUID

class BlePeripheralService : Service() {

    companion object {
        private const val TAG = "SOMA-BLE-Peripheral"

        // UUIDهای نمایشی برای دمو
        val SERVICE_UUID: UUID = UUID.fromString("000018FF-0000-1000-8000-00805F9B34FB")
        val CHAR_AMOUNT_UUID: UUID = UUID.fromString("00002AFF-0000-1000-8000-00805F9B34FB")
        val CHAR_PROOF_UUID: UUID   = UUID.fromString("00002AFE-0000-1000-8000-00805F9B34FB")

        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    private var btManager: BluetoothManager? = null
    private var btAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null

    override fun onCreate() {
        super.onCreate()
        btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        btAdapter = btManager?.adapter

        // راه‌اندازی GATT Server مینیمال
        gattServer = btManager?.openGattServer(this, gattCallback)
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val chAmount = BluetoothGattCharacteristic(
            CHAR_AMOUNT_UUID,
            // Read/Write برای دمو
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        chAmount.addDescriptor(cccd)

        val chProof = BluetoothGattCharacteristic(
            CHAR_PROOF_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(chAmount)
        service.addCharacteristic(chProof)
        gattServer?.addService(service)

        Log.d(TAG, "GATT server created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // در صورت نیاز می‌تونیم اینجا تبلیغ (advertising) رو هم اضافه کنیم
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        gattServer?.close()
        gattServer = null
        Log.d(TAG, "GATT server closed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: android.bluetooth.BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            // پاسخ ساختگی برای دمو
            val value = when (characteristic?.uuid) {
                CHAR_AMOUNT_UUID -> "100000".encodeToByteArray()
                CHAR_PROOF_UUID  -> "OK".encodeToByteArray()
                else -> ByteArray(0)
            }
            gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, 0, value)
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
            // برای دمو فقط OK می‌دهیم
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, android.bluetooth.BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            Log.d(TAG, "Write on ${characteristic?.uuid}: ${value?.size ?: 0} bytes")
        }
    }
}
