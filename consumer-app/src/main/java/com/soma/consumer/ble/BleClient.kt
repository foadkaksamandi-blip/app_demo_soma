package com.soma.consumer.ble

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.UUID

/**
 * BLE Client: اسکن سرویس فروشنده و ارسال JSON مبلغ.
 * استفاده: BleClient.pay(ctx, amount, wallet) ⇒ Boolean
 */
object BleClient {

    private val SERVICE_UUID: UUID = UUID.fromString("5e6d1a70-9f59-4d7e-9a2b-ff7b20a90a10")
    private val CHAR_RX_WRITE_UUID: UUID = UUID.fromString("a3af3c56-2a2a-47cc-87a8-2f7d0f87b001")
    private val CHAR_TX_NOTIFY_UUID: UUID = UUID.fromString("b7c2d6aa-2cf2-4b8f-9d0d-7c0f8b87b002")

    suspend fun pay(ctx: Context, amount: Long, wallet: String = "اصلی"): Boolean = withContext(Dispatchers.IO) {
        val bt = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bt == null || !bt.isEnabled) return@withContext false

        val found = CompletableDeferred<BluetoothDevice?>()
        val scanner = bt.bluetoothLeScanner ?: return@withContext false

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device != null) {
                    found.complete(result.device)
                    scanner.stopScan(this)
                }
            }
        }

        scanner.startScan(listOf(filter), settings, cb)
        val device = try { found.await() } catch (_: Exception) { null } ?: return@withContext false

        val connReady = CompletableDeferred<BluetoothGatt>()
        val gatt = device.connectGatt(ctx, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!connReady.isCompleted) connReady.completeExceptionally(IllegalStateException("disconnected"))
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) connReady.complete(gatt)
                else connReady.completeExceptionally(IllegalStateException("services failed"))
            }
        })

        val readyGatt = try { connReady.await() } catch (_: Exception) { gatt.close(); return@withContext false }

        val service = readyGatt.getService(SERVICE_UUID) ?: run { readyGatt.close(); return@withContext false }
        val rx = service.getCharacteristic(CHAR_RX_WRITE_UUID) ?: run { readyGatt.close(); return@withContext false }
        val tx = service.getCharacteristic(CHAR_TX_NOTIFY_UUID)

        val ackWait = CompletableDeferred<Boolean>()
        if (tx != null) {
            readyGatt.setCharacteristicNotification(tx, true)
        }

        val payload = JSONObject()
            .put("amount", amount)
            .put("wallet", wallet)
            .put("nonce", System.currentTimeMillis().toString() + "-" + (1000..9999).random())
            .put("ts", System.currentTimeMillis())
            .toString()
            .toByteArray(Charset.forName("UTF-8"))

        rx.value = payload
        val okWrite = readyGatt.writeCharacteristic(rx)
        if (!okWrite) { readyGatt.close(); return@withContext false }

        // اگر notify برقرار نشد، با write موفق هم قبول می‌کنیم
        // برای رسیو ack، می‌توان callback اضافه کرد؛ در نسخهٔ کوتاه، 300ms صبر:
        try { Thread.sleep(300) } catch (_: Exception) { }
        readyGatt.close()
        true
    }
}
