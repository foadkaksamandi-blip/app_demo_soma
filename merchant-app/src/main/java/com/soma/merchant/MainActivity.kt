package com.soma.merchant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.soma.merchant.data.TxStore
import com.soma.merchant.data.WalletType
import com.soma.merchant.qr.QRGen
import com.soma.merchant.ui.SOMAMerchantTheme
import com.soma.merchant.ble.BlePeripheralService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var store: TxStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TxStore(this)
        setContent { SOMAMerchantTheme { Screen() } }
    }

    @Composable
    fun Screen() {
        var activeWallet by remember { mutableStateOf(WalletType.MAIN) }
        var amount by remember { mutableStateOf("") }
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("آپ آفلاین سوما 🏪", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text("اپ فروشنده", color = MaterialTheme.colorScheme.onPrimary)
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
                Button(onClick = { startBleReceive() }) { Text("دریافت با بلوتوث") }
                Button(onClick = {
                    val data = JSONObject()
                        .put("merchantId", "m-" + UUID.randomUUID().toString().take(8))
                        .put("walletType", activeWallet.name)
                        .put("amount", amount.toLongOrNull() ?: 0L)
                        .put("ts", System.currentTimeMillis())
                        .toString()
                    qrBitmap = QRGen.make(data)
                }) { Text("تولید QR پرداخت") }
            }

            Spacer(Modifier.height(20.dp))
            qrBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(180.dp))
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { showHistory(activeWallet) }) { Text("📜 تاریخچه تراکنش‌ها") }
        }
    }

    private fun showHistory(wallet: WalletType) {
        val txs = store.list().filter { it.wallet == wallet }
        val msg = txs.joinToString("\n") { "${it.time} — ${it.type}: ${it.amount} تومان" }
        Toast.makeText(this, msg.ifEmpty { "تراکنشی نیست" }, Toast.LENGTH_LONG).show()
    }

    private fun startBleReceive() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED) return
        val i = Intent(this, BlePeripheralService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            startForegroundService(i) else startService(i)
        Toast.makeText(this, "در حالت دریافت بلوتوث قرار گرفت", Toast.LENGTH_LONG).show()
    }
}
