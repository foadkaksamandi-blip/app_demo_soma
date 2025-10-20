package com.soma.merchant

import android.Manifest
import android.content.Intent
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
import com.soma.merchant.ble.BlePeripheralService

class MainActivity : AppCompatActivity() {

    private var balance = 5_000_000

    private lateinit var txtBalance: TextView
    private lateinit var edtAmount: EditText
    private lateinit var btnStartBle: Button
    private lateinit var btnGenQr: Button
    private lateinit var btnScanProof: Button
    private lateinit var btnHistory: Button
    private lateinit var imgQr: ImageView

    // ---- Permissions launcher (BLE + Camera) ----
    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // بعد از دریافت مجوزها، کار خاصی لازم نیست؛ کاربر دوباره دکمه‌ها را می‌زند.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --------- UI برنامه‌ای (بدون XML) ----------
        val root = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(wrap)

        val title1 = TextView(this).apply {
            text = "آپ آفلاین سوما 🏪"
            textSize = 20f
            setTextColor(Color.BLACK)
        }
        val title2 = TextView(this).apply {
            text = "اپ فروشنده"
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

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnStartBle = Button(this).apply { text = "دریافت با بلوتوث" }
        btnGenQr = Button(this).apply { text = "تولید QR پرداخت" }
        row1.addView(btnStartBle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 16, 8, 16) })
        row1.addView(btnGenQr, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 16, 8, 16) })

        imgQr = ImageView(this).apply {
            adjustViewBounds = true
        }

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnScanProof = Button(this).apply { text = "اسکن اثبات پرداخت" }
        btnHistory = Button(this).apply { text = "📜 تاریخچه تراکنش‌ها" }
        row2.addView(btnScanProof, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 16, 8, 16) })
        row2.addView(btnHistory, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8, 16, 8, 16) })

        wrap.addView(title1)
        wrap.addView(title2)
        wrap.addView(txtBalance)
        wrap.addView(edtAmount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        wrap.addView(row1)
        wrap.addView(imgQr, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 700))
        wrap.addView(row2)

        setContentView(root)

        // --------- رویداد دکمه‌ها ----------
        btnStartBle.setOnClickListener { startBleReceive() }

        btnGenQr.setOnClickListener {
            val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
            if (amt <= 0) {
                toast("مبلغ خرید را صحیح وارد کنید")
                return@setOnClickListener
            }
            // QR فاکتور فروشنده (ساده برای دمو)
            val payload = "INVOICE:$amt"
            imgQr.setImageBitmap(makeQr(payload))
            toast("QR پرداخت تولید شد")
        }

        btnScanProof.setOnClickListener {
            // اسکن QR مشتری (در این دمو انتظار: PAY:<amount>)
            requestCameraPermissionThenScan()
        }

        btnHistory.setOnClickListener {
            // در این نسخه‌ی ساده، فقط موجودی فعلی را نمایش می‌دهیم
            AlertDialog.Builder(this)
                .setTitle("تاریخچه تراکنش‌ها")
                .setMessage("در این نسخهٔ دمو، تاریخچهٔ کامل ساده‌سازی شده است.\nموجودی فعلی: $balance تومان")
                .setPositiveButton("بستن", null)
                .show()
        }

        // درخواست اولیهٔ مجوزها (اختیاری؛ کاربر می‌تواند هنگام فشردن دکمه‌ها هم مجوز دهد)
        requestBasicPermissions()
    }

    // ---------- BLE ----------
    private fun startBleReceive() {
        // مجوزها
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_ADVERTISE
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_SCAN
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            needed += Manifest.permission.BLUETOOTH_CONNECT

        if (needed.isNotEmpty()) {
            permsLauncher.launch(needed.toTypedArray())
            return
        }

        val i = Intent(this, BlePeripheralService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        toast("در حالت دریافت بلوتوث قرار گرفت")
    }

    // ---------- QR ----------
    private fun requestCameraPermissionThenScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        // ZXing external scanner
        IntentIntegrator(this).apply {
            setPrompt("اسکن QR مشتری...")
            setBeepEnabled(false)
            setOrientationLocked(true)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (res != null && res.contents != null) {
            handleScannedQr(res.contents)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleScannedQr(text: String) {
        // دمو: انتظار داریم PAY:<amount>
        if (text.startsWith("PAY:")) {
            val amt = text.removePrefix("PAY:").toLongOrNull() ?: 0L
            if (amt > 0) {
                balance += amt.toInt()
                txtBalance.text = "موجودی: ${balance} تومان"
                toast("پرداخت ثبت شد (+$amt)")
            } else {
                toast("QR نامعتبر (مبلغ)")
            }
        } else {
            toast("QR نامعتبر")
        }
    }

    // ---------- Utils ----------
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
        if (list.isNotEmpty()) permsLauncher.launch(list.toTypedArray())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
