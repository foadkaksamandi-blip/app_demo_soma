package com.soma.merchant

import android.Manifest
import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.integration.android.IntentIntegrator
import com.soma.merchant.ble.BlePeripheralService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var balance = 5_000_000

    private lateinit var txtBalance: TextView
    private lateinit var edtAmount: EditText
    private lateinit var btnStartBle: Button
    private lateinit var btnGenQr: Button
    private lateinit var btnScanProof: Button
    private lateinit var btnHistory: Button
    private lateinit var imgQr: ImageView

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#059669")) // سبز قفل‌شده
        }
        root.addView(wrap)

        val title = TextView(this).apply { text="آپ آفلاین سوما 🏪"; textSize=22f; setTextColor(Color.WHITE); gravity=Gravity.CENTER }
        val subtitle = TextView(this).apply { text="اپ فروشنده"; textSize=16f; setTextColor(Color.WHITE); gravity=Gravity.CENTER }
        wrap.addView(title); wrap.addView(subtitle)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(8,16,8,16); setBackgroundColor(Color.parseColor("#111111")) }
        fun mk(label:String) = TextView(this).apply { text=label; setTextColor(Color.WHITE); setPadding(18,12,18,12) }
        tabs.addView(mk("MAIN")); tabs.addView(mk("CBDC")); tabs.addView(mk("SUBSIDY")); tabs.addView(mk("EMERGENCY"))
        wrap.addView(tabs)

        txtBalance = TextView(this).apply { text = "موجودی: $balance تومان"; setTextColor(Color.WHITE); textSize=16f }
        wrap.addView(txtBalance)

        edtAmount = EditText(this).apply { hint="مبلغ خرید"; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); inputType = android.text.InputType.TYPE_CLASS_NUMBER; textDirection = View.TEXT_DIRECTION_RTL }
        wrap.addView(edtAmount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        btnStartBle = Button(this).apply { text="دریافت با بلوتوث" }
        btnGenQr = Button(this).apply { text="تولید QR پرداخت" }
        row.addView(btnStartBle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,8,8,8) })
        row.addView(btnGenQr, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,8,8,8) })
        wrap.addView(row)

        imgQr = ImageView(this).apply { adjustViewBounds = true }
        wrap.addView(imgQr, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 700))

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnScanProof = Button(this).apply { text="اسکن اثبات پرداخت" }
        btnHistory = Button(this).apply { text="📜 تاریخچه تراکنش‌ها" }
        row2.addView(btnScanProof, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(btnHistory, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        wrap.addView(row2)

        setContentView(root)

        btnGenQr.setOnClickListener { generateInvoiceQr() }
        btnStartBle.setOnClickListener { startBleService() }
        btnScanProof.setOnClickListener { scanProof() }
        btnHistory.setOnClickListener { showHistoryDialog() }

        requestBasicPermissions()
    }

    private fun generateInvoiceQr() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) { toast("مبلغ را صحیح وارد کنید"); return }
        val payload = "INVOICE:$amt"
        imgQr.setImageBitmap(makeQr(payload))
        toast("QR فاکتور تولید شد")
    }

    private fun scanProof() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permsLauncher.launch(arrayOf(Manifest.permission.CAMERA)); return
        }
        IntentIntegrator(this).apply {
            setPrompt("اسکن اثبات پرداخت مشتری...")
            setBeepEnabled(false)
            setOrientationLocked(false)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (res != null && res.contents != null) {
            handleProof(res.contents)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleProof(text: String) {
        if (!text.startsWith("PAY:")) { toast("QR نامعتبر"); return }
        val amt = text.removePrefix("PAY:").toLongOrNull() ?: 0L
        if (amt <= 0) { toast("QR نامعتبر"); return }
        balance += amt.toInt()
        txtBalance.text = "موجودی: $balance تومان"
        saveTxHistory("QR_RECEIVE", amt)
        toast("دریافت ثبت شد (+$amt)")
    }

    private fun startBleService() {
        // درخواست مجوزها
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_ADVERTISE
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_CONNECT
        if (needed.isNotEmpty()) { permsLauncher.launch(needed.toTypedArray()); return }
        // start service
        val i = Intent(this, BlePeripheralService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        toast("حالت انتظار بلوتوث فعال شد")
    }

    // QR generator utility
    private fun makeQr(data: String): Bitmap {
        val size=640
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (bits[x,y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun requestBasicPermissions() {
        val list = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) list += Manifest.permission.CAMERA
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) list += Manifest.permission.BLUETOOTH_ADVERTISE
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) list += Manifest.permission.BLUETOOTH_CONNECT
        if (list.isNotEmpty()) permsLauncher.launch(list.toTypedArray())
    }

    private fun saveTxHistory(type:String, amount:Long) {
        val prefs = getSharedPreferences("soma_demo", Context.MODE_PRIVATE)
        val existing = prefs.getString("history", "") ?: ""
        val now = jalaliNow()
        val entry = "$now|$type|$amount"
        val updated = if (existing.isEmpty()) entry else "$existing;;$entry"
        prefs.edit().putString("history", updated).apply()
    }
    private fun loadTxHistory(): List<String> {
        val prefs = getSharedPreferences("soma_demo", Context.MODE_PRIVATE)
        val s = prefs.getString("history", "") ?: ""
        if (s.isEmpty()) return emptyList()
        return s.split(";;")
    }
    private fun showHistoryDialog() {
        val items = loadTxHistory().reversed()
        val msg = if (items.isEmpty()) "هیچ تراکنشی وجود ندارد" else items.joinToString("\n") { it }
        AlertDialog.Builder(this).setTitle("تاریخچه تراکنش‌ها").setMessage(msg).setPositiveButton("بستن", null).show()
    }

    private fun toast(s:String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun jalaliNow(): String {
        val g = Calendar.getInstance()
        val gy = g.get(Calendar.YEAR); val gm = g.get(Calendar.MONTH)+1; val gd = g.get(Calendar.DAY_OF_MONTH)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val j = gregorianToJalali(gy, gm, gd)
        return "${j[0]}/${"%02d".format(j[1])}/${"%02d".format(j[2])} $time"
    }
    private fun gregorianToJalali(gy:Int, gm:Int, gd:Int): IntArray {
        val g_d_m = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var jy:Int; var jm:Int; var jd:Int
        var gy2=gy-1600; var gm2=gm-1; var gd2=gd-1
        var g_day_no = 365*gy2 + (gy2+3)/4 - (gy2+99)/100 + (gy2+399)/400
        g_day_no += g_d_m[gm2] + gd2
        if (gm2>1 && ((gy%4==0 && gy%100!=0) || (gy%400==0))) g_day_no++
        var j_day_no = g_day_no - 79
        val j_np = j_day_no/12053
        j_day_no %= 12053
        jy = 979 + 33*j_np + 4*(j_day_no/1461)
        j_day_no %= 1461
        if (j_day_no>=366) { jy += (j_day_no-366)/365; j_day_no = (j_day_no-366)%365 }
        val j_month_days = intArrayOf(31,31,31,31,31,31,30,30,30,30,30,29)
        var i=0
        while (i<11 && j_day_no>=j_month_days[i]) { j_day_no -= j_month_days[i]; i++ }
        jm = i+1; jd = j_day_no+1
        return intArrayOf(jy,jm,jd)
    }
}
