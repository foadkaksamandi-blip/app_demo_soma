package com.soma.consumer.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BleSpec {
    val SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
    val RX_CHAR_UUID: UUID = UUID.fromString("0000aa01-0000-1000-8000-00805f9b34fb") // consumer writes payment
    val TX_CHAR_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb") // merchant notifies ACK
}

class BleClient(private val ctx: Context) {
    private val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private var onAck: ((String)->Unit)? = null

    suspend fun connectToMerchant(): BluetoothDevice {
        val ad = adapter ?: throw IllegalStateException("Bluetooth disabled")
        val scanner = ad.bluetoothLeScanner ?: throw IllegalStateException("No BLE scanner")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleSpec.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return suspendCancellableCoroutine { cont ->
            val cb = object: ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    cont.resume(result.device)
                }
                override fun onScanFailed(errorCode: Int) {
                    cont.resumeWithException(RuntimeException("Scan failed: $errorCode"))
                }
            }
            scanner.startScan(listOf(filter), settings, cb)
            cont.invokeOnCancellation { runCatching { scanner.stopScan(cb) } }
        }
    }

    suspend fun gattConnect(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { cont ->
            val cb = object: BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED && cont.isActive) {
                        cont.resumeWithException(RuntimeException("GATT disconnected"))
                    }
                }
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val srv = gatt.getService(BleSpec.SERVICE_UUID)
                        ?: return cont.resumeWithException(RuntimeException("Service not found"))
                    rxChar = srv.getCharacteristic(BleSpec.RX_CHAR_UUID)
                    txChar = srv.getCharacteristic(BleSpec.TX_CHAR_UUID)
                    this@BleClient.gatt = gatt
                    gatt.setCharacteristicNotification(txChar, true)
                    cont.resume(gatt)
                }
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (characteristic.uuid == BleSpec.TX_CHAR_UUID) {
                        val s = characteristic.value?.toString(Charset.forName("UTF-8")) ?: return
                        onAck?.invoke(s)
                    }
                }
            }
            @Suppress("MissingPermission")
            val g = device.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation { runCatching { g.disconnect(); g.close() } }
        }

    /**
     * ارسال پرداخت و دریافت ACK امضاشده به‌صورت JSON
     * payload: {"type":"payment","walletType":"MAIN|CBDC|SUBSIDY|EMERGENCY","amount":123,"txId":"..."}
     * خروجی: متن JSON ACK یا null در Timeout
     */
    @Suppress("MissingPermission")
    suspend fun payAndWaitAckJson(walletType: String, amount: Long, txId: String, timeoutMs: Long = 8000): String? {
        val g = gatt ?: throw IllegalStateException("Not connected")
        val c = rxChar ?: throw IllegalStateException("RX not found")
        val payload = JSONObject()
            .put("type", "payment")
            .put("walletType", walletType)
            .put("amount", amount)
            .put("txId", txId)
            .toString()
        return suspendCancellableCoroutine { cont ->
            val timer = Timer()
            timer.schedule(object: TimerTask(){ override fun run() {
                if (cont.isActive) cont.resume(null)
            }}, timeoutMs)
            onAck = { json ->
                timer.cancel()
                if (cont.isActive) cont.resume(json)
            }
            c.value = payload.toByteArray(Charsets.UTF_8)
            if (!g.writeCharacteristic(c)) {
                timer.cancel()
                cont.resumeWithException(RuntimeException("writeCharacteristic failed"))
            }
            cont.invokeOnCancellation { timer.cancel() }
        }
    }

    fun close() { gatt?.disconnect(); gatt?.close() }
}
