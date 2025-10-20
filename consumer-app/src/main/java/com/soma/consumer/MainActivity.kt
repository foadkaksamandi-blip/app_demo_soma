package com.soma.consumer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import com.soma.consumer.ble.BleClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    // موجودی اولیه خریدار
    private var balance = 10_000_000

    // UI
    private lateinit var txtBalance: TextView
    private lateinit var edtAmount: EditText
    private lateinit var btnPayBle: Button
    private lateinit var btnPayQr: Button
    private lateinit var btnHistory: Button
    private lateinit var imgProofQr: ImageView

    // BLE
    private lateinit var bleClient: BleClient

    // لانچر درخواست مجوزها
    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleClient = BleClient(this)

        // ---------- ساخت UI برنامه‌ای (بدون XML) ----------
        val root = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(wrap)

        val title1 = TextView(this).apply {
            text = "آپ آفلاین سوما 👤"
            textSize = 20f
            setTextColor(Color.BLACK)
        }
        val title2 = TextView(this).apply {
            text = "اپ خریدار"
            textSize = 16f
        }
        txtBalance = TextView(this).apply {
            text = "موجودی: ${balance} تومان"
            textSize = 16f
        }
        edtAmount = EditText(this).apply {
            hint = "مبلغ خرید"
            textDirection = View.TEXT_DIRECTION_RTL
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val rowBtns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnPayBle = Button(this).apply { text = "پرداخت با بلوتوث" }
        btnPayQr  = Button(this).apply { text = "پرداخت با کد QR" }
        rowBtns.addView(btnPayBle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,16,8,16) })
        rowBtns.addView(btnPayQr,  LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,16,8,16) })

        imgProofQr = ImageView(this).apply { adjustViewBounds = true }

        btnHistory = Button(this).apply { text = "📜 تاریخچه تراکنش‌ها" }

        wrap.addView(title1)
        wrap.addView(title2)
        wrap.addView(txtBalance)
        wrap.addView(edtAmount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        wrap.addView(rowBtns)
        wrap.addView(imgProofQr, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 700))
        wrap.addView(btnHistory)

        setContentView(root)

        // ---------- رویداد دکمه‌ها ----------
        btnPayQr.setOnClickListener { payWithQr() }
        btnPayBle.setOnClickListener { payWithBle() }
        btnHistory.setOnClickListener {
            Toast.makeText(this, "در نسخهٔ دمو، تاریخچهٔ کامل ساده‌سازی شده است.", Toast.LENGTH_SHORT).show()
        }

        requestBasicPermissions()
    }

    // ---------------- QR Flow ----------------
    private fun payWithQr() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) {
            toast("مبلغ خرید را صحیح وارد کنید")
            return
        }
        if (amt > balance) {
            toast("موجودی کافی نیست")
            return
        }
        // اجازه دوربین
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        // اسکن QR فاکتور فروشنده (INVOICE:<amount>)
        IntentIntegrator(this).apply {
            setPrompt("اسکن QR فروشنده…")
            setBeepEnabled(false)
            setOrientationLocked(true)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (res != null && res.contents != null) {
            handleScannedInvoice(res.contents)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleScannedInvoice(text: String) {
        // انتظار داریم INVOICE:<amount>
        if (!text.startsWith("INVOICE:")) {
            toast("QR نامعتبر")
            return
        }
        val invoiceAmount = text.removePrefix("INVOICE:").toLongOrNull() ?: 0L
        val entered = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (invoiceAmount <= 0 || invoiceAmount != entered) {
            toast("مغایرت مبلغ فاکتور/ورودی")
            return
        }
        if (invoiceAmount > balance) {
            toast("موجودی کافی نیست")
            return
        }

        // کسر موجودی و ساخت QR اثبات پرداخت برای فروشنده
        balance -= invoiceAmount.toInt()
        txtBalance.text = "موجودی: ${balance} تومان"

        val proofPayload = "PAY:$invoiceAmount"
        imgProofQr.setImageBitmap(makeQr(proofPayload))
        toast("پرداخت انجام شد — QR اثبات را به فروشنده نشان دهید")
    }

    // ---------------- BLE Flow ----------------
    private fun payWithBle() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) { toast("مبلغ خرید را صحیح وارد کنید"); return }
        if (amt > balance) { toast("موجودی کافی نیست"); return }

        // مجوزهای بلوتوث (+ لوکیشن برای برخی ورژن‌ها)
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_SCAN
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.ACCESS_FINE_LOCATION

        if (needed.isNotEmpty()) {
            permsLauncher.launch(needed.toTypedArray())
            return
        }

        // روشن بودن بلوتوث
        val bm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bm.adapter
        if (adapter == null || !adapter.isEnabled) {
            toast("بلوتوث فعال نیست")
            return
        }

        // اتصال و پرداخت
        GlobalScope.launch {
            try {
                val device = bleClient.connectToMerchant()          // اسکن و پیدا کردن فروشنده
                bleClient.gattConnect(device)                        // اتصال GATT
                val txId = UUID.randomUUID().toString()
                val ack = bleClient.payAndWaitAckJson("MAIN", amt, txId, timeoutMs = 10_000)
                runOnUiThread {
                    if (ack != null) {
                        balance -= amt.toInt()
                        txtBalance.text = "موجودی: ${balance} تومان"
                        toast("پرداخت بلوتوثی موفق ✅")
                    } else {
                        toast("پاسخ از فروشنده دریافت نشد")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { toast("اتصال بلوتوث برقرار نشد") }
            } finally {
                bleClient.close()
            }
        }
    }

    // ---------------- Utils ----------------
    private fun makeQr(data: String): Bitmap {
        val size = 720
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
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
