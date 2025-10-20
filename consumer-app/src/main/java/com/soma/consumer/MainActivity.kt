package com.soma.consumer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.soma.consumer.ble.BleClient
import com.soma.consumer.data.TxStore
import com.soma.consumer.data.WalletType
import com.soma.consumer.qr.QRGen
import com.soma.consumer.ui.SOMAConsumerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var store: TxStore
    private lateinit var ble: BleClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TxStore(this)
        ble = BleClient(this)
        setContent { SOMAConsumerTheme { Screen() } }
    }

    // --- helper: Persian labels for wallets
    private fun walletLabel(w: WalletType): String = when (w) {
        WalletType.MAIN -> "اصلی"
        WalletType.CBDC -> "رمزارز ملی"
        WalletType.SUBSIDY -> "یارانه"
        WalletType.EMERGENCY -> "کالابرگ اضطراری"
    }

    @Composable
    fun Screen() {
        var activeWallet by remember { mutableStateOf(WalletType.MAIN) }
        var amount by remember { mutableStateOf("") }
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var showHistoryDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
                .verticalScroll(scroll)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("آپ آفلاین سوما 👤", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text("اپ خریدار", color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = activeWallet.ordinal) {
                WalletType.values().forEachIndexed { idx, w ->
                    Tab(selected = idx == activeWallet.ordinal, onClick = { activeWallet = w },
                        text = { Text(walletLabel(w)) })
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("موجودی: ${store.balance(activeWallet)} تومان", color = MaterialTheme.colorScheme.onPrimary)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("مبلغ خرید") },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    // permission checks for BLE
                    requestBlePermissionsIfNeeded {
                        scope.launch { payByBle(activeWallet, amount.toLongOrNull() ?: 0L) }
                    }
                }) {
                    Text("پرداخت با بلوتوث")
                }
                Button(onClick = { startQrScanMerchant() }) {
                    Text("پرداخت با کد QR")
                }
            }

            Spacer(Modifier.height(20.dp))

            // After a merchant QR scanned, this shows payment proof QR for merchant to scan
            qrBitmap?.let {
                Text("QR اثبات پرداخت (نمایش به فروشنده):", color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.height(8.dp))
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp))
            }

            Spacer(Modifier.height(20.dp))

            Button(onClick = { showHistoryDialog = true }) {
                Text("📜 تاریخچه تراکنش‌ها")
            }

            if (showHistoryDialog) {
                HistoryDialog(onDismiss = { showHistoryDialog = false })
            }
        }
    }

    @Composable
    fun HistoryDialog(onDismiss: () -> Unit) {
        val txs = store.list().sortedByDescending { it.time }
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("بستن") }
            },
            title = { Text("تاریخچه تراکنش‌ها") },
            text = {
                Column {
                    if (txs.isEmpty()) Text("تراکنشی ثبت نشده")
                    else txs.forEach { t ->
                        Text("${t.time} — ${t.type}: ${t.amount} تومان")
                        Divider()
                    }
                }
            }
        )
    }

    // ---------- QR flows ----------
    private fun startQrScanMerchant() {
        // scan merchant QR
        val integrator = IntentIntegrator(this)
        integrator.setPrompt("اسکن QR فروشنده...")
        integrator.setBeepEnabled(false)
        integrator.initiateScan()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            handleScanResult(result.contents)
        }
    }

    private fun handleScanResult(contents: String) {
        try {
            val json = JSONObject(contents)
            val type = json.optString("type", "merchant_qr")
            if (type == "merchant_qr") {
                // merchant QR scanned: create payment proof after deducting balance
                val merchantId = json.getString("merchantId")
                val amount = json.getLong("amount")
                val wallet = WalletType.valueOf(json.getString("walletType"))

                if (store.balance(wallet) >= amount) {
                    // deduct immediately (consumer side)
                    val bal = store.balance(wallet)
                    store.setBalance(wallet, bal - amount)
                    store.add(-amount, "پرداخت QR", wallet)

                    // create payment proof JSON (to show to merchant)
                    val txId = "c-" + UUID.randomUUID().toString().take(8)
                    val proof = JSONObject()
                        .put("type", "payment_proof")
                        .put("merchantId", merchantId)
                        .put("amount", amount)
                        .put("walletType", wallet.name)
                        .put("txId", txId)
                        .put("ts", System.currentTimeMillis())
                        .toString()
                    qrBitmap = QRGen.make(proof)
                    Toast.makeText(this, "پرداخت انجام شد — QR اثبات را به فروشنده نشان دهید", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "موجودی کافی نیست", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "QR نامعتبر", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در پردازش QR", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- BLE flow ----------
    private fun requestBlePermissionsIfNeeded(onDone: () -> Unit) {
        val perms = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        if (perms.isEmpty()) { onDone(); return }
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val ok = results.values.all { it }
            if (ok) onDone() else Toast.makeText(this, "مجوزهای بلوتوث نیاز است", Toast.LENGTH_SHORT).show()
        }
        launcher.launch(perms.toTypedArray())
    }

    private fun payByBle(wallet: WalletType, amount: Long) {
        if (amount <= 0) { Toast.makeText(this, "مبلغ نامعتبر", Toast.LENGTH_SHORT).show(); return }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val device = ble.connectToMerchant()
                ble.gattConnect(device)
                val txId = UUID.randomUUID().toString()
                val ackJson = ble.payAndWaitAckJson(wallet.name, amount, txId)
                runOnUiThread {
                    if (ackJson != null) {
                        // parse merchant ack (signed) but demo: trust it
                        try {
                            val ack = JSONObject(ackJson)
                            val bal = store.balance(wallet)
                            if (bal >= amount) {
                                store.setBalance(wallet, bal - amount)
                                store.add(-amount, "پرداخت BLE", wallet)
                                Toast.makeText(this@MainActivity, "تراکنش BLE موفق ✅", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "موجودی کافی نیست", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) { Toast.makeText(this@MainActivity, "ACK نامعتبر", Toast.LENGTH_SHORT).show() }
                    } else Toast.makeText(this@MainActivity, "اتصال یا ACK دریافت نشد", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "BLE خطا: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally { ble.close() }
        }
    }
}
