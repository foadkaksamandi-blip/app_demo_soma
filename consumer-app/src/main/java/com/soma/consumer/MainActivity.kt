package com.soma.consumer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import com.soma.consumer.ble.BleClient
import com.soma.consumer.data.TxStore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import shared.utils.DateUtils
import java.util.*

class MainActivity : AppCompatActivity() {

    // رنگ‌ها
    private val GreenBg = Color.parseColor("#059669")
    private val BtnPurple = Color.parseColor("#7C3AED")
    private val BtnAmber  = Color.parseColor("#F59E0B")
    private val BtnBlue   = Color.parseColor("#2563EB")
    private val BtnPink   = Color.parseColor("#EC4899")
    private val WhiteText = Color.WHITE
    private val MutedWhite = Color.argb(160, 255, 255, 255)

    // Store
    private lateinit var store: TxStore

    // UI
    private lateinit var txtTitle1: TextView
    private lateinit var txtTitle2: TextView
    private lateinit var txtNow: TextView
    private lateinit var txtBalance: TextView
    private lateinit var edtAmount: EditText
    private lateinit var btnPayBle: Button
    private lateinit var btnPayQr: Button
    private lateinit var btnWalletMain: Button
    private lateinit var btnWalletCBDC: Button
    private lateinit var btnWalletSubsidy: Button
    private lateinit var btnWalletEmergency: Button
    private lateinit var btnHistory: Button
    private lateinit var imgProofQr: ImageView

    // BLE
    private lateinit var bleClient: BleClient

    // مجوزها
    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    // کیف فعال
    private var activeWallet: String = "اصلی"

    // زمان زنده
    private val handler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            try { txtNow.text = "تاریخ و ساعت: " + DateUtils.nowJalaliDateTime() } catch (_: Exception) {}
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TxStore(this)
        bleClient = BleClient(this)

        // UI
        val root = ScrollView(this).apply { setBackgroundColor(GreenBg) }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(wrap)

        txtTitle1 = TextView(this).apply { text = "آپ آفلاین سوما 👤"; textSize = 22f; setTextColor(WhiteText) }
        txtTitle2 = TextView(this).apply { text = "اپ خریدار"; textSize = 16f; setTextColor(MutedWhite) }
        txtNow = TextView(this).apply { text = "تاریخ و ساعت: "; textSize = 13f; setTextColor(MutedWhite) }
        txtBalance = TextView(this).apply {
            text = "موجودی: ${store.balance(activeWallet)} تومان"
            textSize = 18f; setTextColor(WhiteText)
        }
        edtAmount = EditText(this).apply {
            hint = "مبلغ خرید"
            textDirection = View.TEXT_DIRECTION_RTL
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(WhiteText); setHintTextColor(MutedWhite)
            backgroundTintList = ColorStateList.valueOf(WhiteText)
        }

        // کیف‌ها
        val rowWallets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        btnWalletMain = fancyBtn("کیف: اصلی", BtnPurple)
        btnWalletCBDC = fancyBtn("رمزارز ملی", BtnAmber)
        btnWalletSubsidy = fancyBtn("یارانه ملی", BtnBlue)
        btnWalletEmergency = fancyBtn("اعتبار اضطراری ملی", BtnPink)
        rowWallets.addView(btnWalletMain, lpWeight()); rowWallets.addView(btnWalletCBDC, lpWeight())
        rowWallets.addView(btnWalletSubsidy, lpWeight()); rowWallets.addView(btnWalletEmergency, lpWeight())

        // پرداخت‌ها
        val rowPay = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        btnPayBle = fancyBtn("پرداخت با بلوتوث", BtnBlue)
        btnPayQr  = fancyBtn("پرداخت با کد QR", BtnAmber)
        rowPay.addView(btnPayBle, lpWeight()); rowPay.addView(btnPayQr, lpWeight())

        imgProofQr = ImageView(this).apply { adjustViewBounds = true }
        btnHistory = fancyBtn("📜 تاریخچه تراکنش‌ها", BtnPurple)

        wrap.addView(txtTitle1); wrap.addView(txtTitle2); wrap.addView(txtNow)
        wrap.addView(spacer(8)); wrap.addView(txtBalance)
        wrap.addView(edtAmount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        wrap.addView(spacer(12)); wrap.addView(rowWallets); wrap.addView(spacer(12)); wrap.addView(rowPay)
        wrap.addView(spacer(16)); wrap.addView(imgProofQr, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 700))
        wrap.addView(spacer(12)); wrap.addView(btnHistory)

        setContentView(root)

        // رفتار کیف‌ها
        btnWalletMain.setOnClickListener { setActiveWallet("اصلی") }
        btnWalletCBDC.setOnClickListener { setActiveWallet("رمزارز ملی") }
        btnWalletSubsidy.setOnClickListener { setActiveWallet("یارانه ملی") }
        btnWalletEmergency.setOnClickListener { setActiveWallet("اعتبار اضطراری ملی") }

        // رویدادها
        btnPayQr.setOnClickListener { payWithQr() }
        btnPayBle.setOnClickListener { payWithBle() }
        btnHistory.setOnClickListener { showHistoryDialog() }

        requestBasicPermissions()
        handler.post(timeRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeRunnable)
        super.onDestroy()
    }

    // Helpers UI
    private fun fancyBtn(title: String, bg: Int): Button =
        Button(this).apply { text = title; setAllCaps(false); setTextColor(WhiteText); backgroundTintList = ColorStateList.valueOf(bg) }

    private fun lpWeight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,16,8,16) }

    private fun spacer(h: Int): View = Space(this).apply { minimumHeight = h }

    private fun setActiveWallet(name: String) {
        activeWallet = name
        txtBalance.text = "موجودی: ${store.balance(activeWallet)} تومان"
        Toast.makeText(this, "کیف فعال: $name", Toast.LENGTH_SHORT).show()
    }

    // ---------- QR ----------
    private fun payWithQr() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) { toast("مبلغ خرید را صحیح وارد کنید"); return }
        if (amt > store.balance(activeWallet)) { toast("موجودی کافی نیست"); return }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permsLauncher.launch(arrayOf(Manifest.permission.CAMERA)); return
        }
        IntentIntegrator(this).apply { setPrompt("اسکن QR فروشنده…"); setBeepEnabled(false); setOrientationLocked(true); initiateScan() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (res != null && res.contents != null) { handleScannedInvoice(res.contents); return }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleScannedInvoice(text: String) {
        if (!text.startsWith("INVOICE:")) { toast("QR نامعتبر"); return }
        val invoiceAmount = text.removePrefix("INVOICE:").toLongOrNull() ?: 0L
        val entered = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (invoiceAmount <= 0 || invoiceAmount != entered) { toast("مغایرت مبلغ فاکتور/ورودی"); return }
        if (invoiceAmount > store.balance(activeWallet)) { toast("موجودی کافی نیست"); return }

        // کسر و ثبت
        val newBal = store.balance(activeWallet) - invoiceAmount
        store.setBalance(activeWallet, newBal)
        store.add(-invoiceAmount, "پرداخت QR", activeWallet)
        txtBalance.text = "موجودی: ${store.balance(activeWallet)} تومان"

        // QR اثبات
        val proofPayload = "PAY:$invoiceAmount"
        imgProofQr.setImageBitmap(makeQr(proofPayload))
        toast("پرداخت انجام شد — QR اثبات را به فروشنده نشان دهید")
    }

    // ---------- BLE ----------
    private fun payWithBle() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) { toast("مبلغ خرید را صحیح وارد کنید"); return }
        if (amt > store.balance(activeWallet)) { toast("موجودی کافی نیست"); return }

        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_SCAN
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (needed.isNotEmpty()) { permsLauncher.launch(needed.toTypedArray()); return }

        val bm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bm.adapter
        if (adapter == null || !adapter.isEnabled) { toast("بلوتوث فعال نیست"); return }

        GlobalScope.launch {
            try {
                val device = bleClient.connectToMerchant()
                bleClient.gattConnect(device)
                val txId = UUID.randomUUID().toString()
                val ack = bleClient.payAndWaitAckJson("MAIN", amt, txId, timeoutMs = 10_000)
                runOnUiThread {
                    if (ack != null) {
                        val newBal = store.balance(activeWallet) - amt
                        store.setBalance(activeWallet, newBal)
                        store.add(-amt, "پرداخت BLE", activeWallet)
                        txtBalance.text = "موجودی: ${store.balance(activeWallet)} تومان"
                        toast("پرداخت بلوتوثی موفق ✅")
                    } else toast("پاسخ از فروشنده دریافت نشد")
                }
            } catch (e: Exception) {
                runOnUiThread { toast("اتصال بلوتوث برقرار نشد") }
            } finally { bleClient.close() }
        }
    }

    // ---------- تاریخچه ----------
    private fun showHistoryDialog() {
        val items = store.list().reversed().take(50).joinToString("\n") {
            val sign = if (it.amount >= 0) "+" else ""
            "${DateUtils.formatJalali(it.time)} — ${it.type} (${it.wallet}): $sign${it.amount} تومان"
        }.ifEmpty { "هیچ تراکنشی ثبت نشده است." }

        AlertDialog.Builder(this)
            .setTitle("📜 تاریخچه تراکنش‌ها")
            .setMessage(items)
            .setPositiveButton("بستن", null)
            .show()
    }

    // Utils
    private fun makeQr(data: String): Bitmap {
        val size = 720
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    private fun requestBasicPermissions() {
        val list = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            list += Manifest.permission.CAMERA
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            list += Manifest.permission.BLUETOOTH_SCAN
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            list += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            list += Manifest.permission.ACCESS_FINE_LOCATION
        if (list.isNotEmpty()) permsLauncher.launch(list.toTypedArray())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
