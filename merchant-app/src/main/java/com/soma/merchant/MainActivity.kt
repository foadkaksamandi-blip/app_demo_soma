package com.soma.merchant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
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
import com.soma.merchant.ble.BlePeripheralService
import com.soma.merchant.data.TxStore
import com.soma.merchant.data.WalletType
import com.soma.merchant.qr.QRGen
import com.soma.merchant.ui.SOMAMerchantTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var store: TxStore
    private val PREF = "merchant"
    private val KEY_MERCHANT_ID = "merchantId"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TxStore(this)
        ensureMerchantId()
        setContent { SOMAMerchantTheme { Screen() } }
    }

    private fun ensureMerchantId() {
        val sp = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_MERCHANT_ID)) {
            sp.edit().putString(KEY_MERCHANT_ID, "m-"+UUID.randomUUID().toString().take(8)).apply()
        }
    }

    private fun getMerchantId(): String =
        getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MERCHANT_ID, "m-unknown")!!

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
        var showHistory by remember { mutableStateOf(false) }
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
            Text("آپ آفلاین سوما 🏪", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text("اپ فروشنده", color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = activeWallet.ordinal) {
                WalletType.values().forEachIndexed { idx, w ->
                    Tab(selected = idx == activeWallet.ordinal, onClick = { activeWallet = w },
                        text = { Text(walletLabel(w)) })
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("موجودی: ${store.balance(activeWallet)} تومان", color = MaterialTheme.colorScheme.onPrimary)
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("مبلغ خرید") },
                modifier = Modifier.fillMaxWidth(0.9f))
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { startBleReceive() }) { Text("دریافت با بلوتوث") }
                Button(onClick = {
                    // generate QR with persistent merchantId
                    val mId = getMerchantId()
                    val amt = amount.toLongOrNull() ?: 0L
                    val data = JSONObject()
                        .put("type", "merchant_qr")
                        .put("merchantId", mId)
                        .put("walletType", activeWallet.name)
                        .put("amount", amt)
                        .put("ts", System.currentTimeMillis())
                        .toString()
                    qrBitmap = QRGen.make(data)
                }) { Text("تولید QR پرداخت") }
            }

            Spacer(Modifier.height(20.dp))
            qrBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp))
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { scanPaymentProof() }) { Text("اسکن اثبات پرداخت") }
                Button(onClick = { showHistory = true }) { Text("📜 تاریخچه تراکنش‌ها") }
            }

            if (showHistory) {
                HistoryDialog(onDismiss = { showHistory = false })
            }
        }
    }

    private fun scanPaymentProof() {
        val integrator = IntentIntegrator(this)
        integrator.setPrompt("اسکن اثبات پرداخت مشتری...")
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
            val type = json.optString("type", "")
            if (type == "payment_proof") {
                val merchantId = json.getString("merchantId")
                val amount = json.getLong("amount")
                val wallet = WalletType.valueOf(json.getString("walletType"))
                val txId = json.getString("txId")
                // validate merchantId matches this merchant
                if (merchantId != getMerchantId()) {
                    Toast.makeText(this, "اثبات پرداخت مربوط به فروشندهٔ دیگر است", Toast.LENGTH_LONG).show()
                    return
                }
                // protect against replay: store will just add and can be extended with replay logic
                store.setBalance(wallet, store.balance(wallet) + amount)
                store.add(amount, "دریافت QR", wallet)
                Toast.makeText(this, "پرداخت ثبت شد — موجودی افزایش یافت", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "QR نامعتبر", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در پردازش QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBleReceive() {
        // request advertise/connect permissions if needed
        val perms = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        if (perms.isNotEmpty()) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                val ok = res.values.all { it }
                if (ok) startPeripheralService() else Toast.makeText(this, "مجوزهای بلوتوث نیاز است", Toast.LENGTH_SHORT).show()
            }
            launcher.launch(perms.toTypedArray())
        } else startPeripheralService()
    }

    private fun startPeripheralService() {
        val i = Intent(this, BlePeripheralService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        Toast.makeText(this, "در حالت دریافت بلوتوث قرار گرفت", Toast.LENGTH_LONG).show()
    }

    @Composable
    fun HistoryDialog(onDismiss: () -> Unit) {
        val txs = store.list().sortedByDescending { it.time }
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
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
}
