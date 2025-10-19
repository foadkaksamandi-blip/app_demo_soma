package com.soma.consumer

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

    @Composable
    fun Screen() {
        var activeWallet by remember { mutableStateOf(WalletType.MAIN) }
        var amount by remember { mutableStateOf("") }
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("آپ آفلاین سوما 👤", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text("اپ خریدار", color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = activeWallet.ordinal) {
                WalletType.values().forEachIndexed { idx, w ->
                    Tab(
                        selected = idx == activeWallet.ordinal,
                        onClick = { activeWallet = w },
                        text = { Text(w.name) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("موجودی: ${store.balance(activeWallet)} تومان", color = MaterialTheme.colorScheme.onPrimary)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("مبلغ خرید") },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { scope.launch { payByBle(activeWallet, amount.toLongOrNull() ?: 0L) } }) {
                    Text("پرداخت با بلوتوث")
                }
                Button(onClick = { startQrScan(activeWallet, amount) }) {
                    Text("پرداخت با QR کد")
                }
            }

            Spacer(Modifier.height(20.dp))
            qrBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp))
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { showHistory(activeWallet) }) { Text("📜 تاریخچه تراکنش‌ها") }
        }
    }

    private fun startQrScan(wallet: WalletType, amount: String) {
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
            try {
                val json = JSONObject(result.contents)
                val merchantId = json.getString("merchantId")
                val amount = json.getLong("amount")
                val wallet = WalletType.valueOf(json.getString("walletType"))
                val ts = json.getLong("ts")
                Toast.makeText(this, "QR تایید شد: ${amount}تومان", Toast.LENGTH_LONG).show()
                val bal = store.balance(wallet)
                if (bal >= amount) {
                    store.setBalance(wallet, bal - amount)
                    store.add(-amount, "پرداخت QR", wallet)
                } else {
                    Toast.makeText(this, "موجودی کافی نیست", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "QR نامعتبر", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showHistory(wallet: WalletType) {
        val txs = store.list().filter { it.wallet == wallet }
        val msg = txs.joinToString("\n") { "${it.time} — ${it.type}: ${it.amount} تومان" }
        Toast.makeText(this, msg.ifEmpty { "تراکنشی نیست" }, Toast.LENGTH_LONG).show()
    }

    private fun payByBle(wallet: WalletType, amount: Long) {
        if (amount <= 0) { Toast.makeText(this, "مبلغ نامعتبر", Toast.LENGTH_SHORT).show(); return }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val device: BluetoothDevice = ble.connectToMerchant()
                ble.gattConnect(device)
                val txId = UUID.randomUUID().toString()
                val ackJson = ble.payAndWaitAckJson(wallet.name, amount, txId)
                runOnUiThread {
                    if (ackJson != null) {
                        val ack = JSONObject(ackJson)
                        val bal = store.balance(wallet)
                        if (bal >= amount) {
                            store.setBalance(wallet, bal - amount)
                            store.add(-amount, "پرداخت BLE", wallet)
                            Toast.makeText(this@MainActivity, "تراکنش موفق ✅", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(this@MainActivity, "موجودی کافی نیست", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this@MainActivity, "اتصال ناموفق", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "BLE خطا: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally { ble.close() }
        }
    }
}
