package com.soma.consumer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private var balanceMain = 10_000_000
    private var balanceCbcd = 0
    private var balanceSubsidy = 0
    private var balanceEmergency = 0

    private lateinit var txtBalanceMain: TextView
    private lateinit var txtBalanceCbdc: TextView
    private lateinit var txtBalanceSubsidy: TextView
    private lateinit var txtBalanceEmergency: TextView

    private lateinit var edtAmount: EditText
    private lateinit var btnPayBle: Button
    private lateinit var btnPayQr: Button
    private lateinit var btnHistory: Button

    private lateinit var accountTabs: LinearLayout

    private val bleClient by lazy { BleClient(this) }

    private val permsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout (RBG background per theme: blue for consumer)
        val root = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E40AF")) // آبی قفل‌شده
        }
        root.addView(wrap)

        // Title
        val title = TextView(this).apply {
            text = "آپ آفلاین سوما 👤"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "اپ خریدار"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        wrap.addView(title)
        wrap.addView(subtitle)

        // Tabs accounts
        accountTabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 16, 8, 16)
            setBackgroundColor(Color.parseColor("#111111"))
        }
        fun mkTab(label: String): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 14f
                setPadding(18, 12, 18, 12)
            }
        }
        accountTabs.addView(mkTab("MAIN"))
        accountTabs.addView(mkTab("CBDC"))
        accountTabs.addView(mkTab("SUBSIDY"))
        accountTabs.addView(mkTab("EMERGENCY"))
        wrap.addView(accountTabs)

        // Balance displays (جمعاً)
        txtBalanceMain = TextView(this).apply { text = "موجودی: ${balanceMain} تومان"; setTextColor(Color.WHITE); textSize=16f }
        wrap.addView(txtBalanceMain)

        // Amount input
        edtAmount = EditText(this).apply {
            hint = "مبلغ خرید"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textDirection = View.TEXT_DIRECTION_RTL
        }
        wrap.addView(edtAmount, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Buttons row
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; }
        btnPayBle = Button(this).apply { text = "پرداخت با بلوتوث" }
        btnPayQr = Button(this).apply { text = "پرداخت با QR کد" }
        row.addView(btnPayBle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,8,8,8) })
        row.addView(btnPayQr, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(8,8,8,8) })
        wrap.addView(row)

        // History button
        btnHistory = Button(this).apply { text = "📜 تاریخچه تراکنش‌ها" }
        wrap.addView(btnHistory, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)

        // Tab clicks: set account context and show balances
        accountTabs.getChildAt(0).setOnClickListener { showAccount("MAIN") }
        accountTabs.getChildAt(1).setOnClickListener { showAccount("CBDC") }
        accountTabs.getChildAt(2).setOnClickListener { showAccount("SUBSIDY") }
        accountTabs.getChildAt(3).setOnClickListener { showAccount("EMERGENCY") }

        btnPayQr.setOnClickListener { startQrFlow() }
        btnPayBle.setOnClickListener { startBleFlow() }
        btnHistory.setOnClickListener { showHistoryDialog() }

        requestBasicPermissions()
    }

    private fun showAccount(id: String) {
        // نمایش موجودی هر کدام در رابط (مخفف)
        when (id) {
            "MAIN" -> txtBalanceMain.text = "موجودی: ${balanceMain} تومان"
            "CBDC" -> txtBalanceMain.text = "رمز ارز ملی: ${balanceCbcd} تومان"
            "SUBSIDY" -> txtBalanceMain.text = "یارانه: ${balanceSubsidy} تومان"
            "EMERGENCY" -> txtBalanceMain.text = "موجودی اضطراری: ${balanceEmergency} تومان"
        }
        toast("اکانت: $id")
    }

    // ---------- QR ----------
    private fun startQrFlow() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) { toast("مبلغ را وارد کنید"); return }
        if (amt > balanceMain) { toast("موجودی کافی نیست"); return }

        // دوربین و اسکن INVOICE:<amount>
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        IntentIntegrator(this).apply {
            setPrompt("QR فاکتور فروشنده را اسکن کنید")
            setBeepEnabled(false)
            setOrientationLocked(false)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (res != null && res.contents != null) {
            handleInvoiceScan(res.contents)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleInvoiceScan(payload: String) {
        if (!payload.startsWith("INVOICE:")) { toast("QR نامعتبر"); return }
        val invoiceAmt = payload.removePrefix("INVOICE:").toLongOrNull() ?: 0L
        val entered = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (invoiceAmt != entered) { toast("مغایرت مبلغ فاکتور و ورودی"); return }
        // کسر
        balanceMain -= invoiceAmt.toInt()
        txtBalanceMain.text = "موجودی: ${balanceMain} تومان"
        // تولید QR اثبات پرداخت
        val proof = "PAY:$invoiceAmt"
        // نمایش کوچک: تولید بیت‌مپ
        val bmp = makeQr(proof)
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle("QR اثبات پرداخت")
            .setMessage("لطفاً QR را به فروشنده نشان دهید")
            .setView(iv)
            .setPositiveButton("بستن", null)
            .create()
        dlg.show()
        saveTxHistory("QR_PAY", invoiceAmt)
    }

    // ---------- BLE ----------
    private fun startBleFlow() {
        val amt = edtAmount.text.toString().toLongOrNull() ?: 0L
        if (amt <= 0) { toast("مبلغ را وارد کنید"); return }
        if (amt > balanceMain) { toast("موجودی کافی نیست"); return }

        // permissions and BT enabled
        val needed = ArrayList<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_SCAN)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.isNotEmpty()) { permsLauncher.launch(needed.toTypedArray()); return }

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) { toast("بلوتوث فعال نیست"); return }

        // connect and send payment JSON using BleClient
        Thread {
            try {
                val device = bleClient.findMerchantDevice(10_000) ?: runOnUiThread { toast("فروشنده پیدا نشد"); return@Thread }
                val ok = bleClient.connectAndSendPayment(device, "MAIN", amt)
                runOnUiThread {
                    if (ok) {
                        balanceMain -= amt.toInt()
                        txtBalanceMain.text = "موجودی: ${balanceMain} تومان"
                        toast("پرداخت بلوتوثی موفق")
                        saveTxHistory("BLE_PAY", amt)
                    } else {
                        toast("پرداخت با فروشنده ناموفق بود")
                    }
                }
            } catch (ex: Exception) {
                runOnUiThread { toast("خطا در پرداخت بلوتوثی") }
            } finally {
                bleClient.close()
            }
        }.start()
    }

    // ---------- Helpers ----------
    private fun makeQr(data: String): Bitmap {
        val size = 640
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    // Simple SharedPreferences history save/load
    private fun saveTxHistory(type: String, amount: Long) {
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
        android.app.AlertDialog.Builder(this).setTitle("تاریخچه تراکنش‌ها").setMessage(msg).setPositiveButton("بستن", null).show()
    }

    private fun requestBasicPermissions() {
        val list = ArrayList<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.CAMERA)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.BLUETOOTH_SCAN)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (list.isNotEmpty()) permsLauncher.launch(list.toTypedArray())
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // Jalali (Persian) date simple converter for display (returns yyyy/MM/dd HH:mm)
    private fun jalaliNow(): String {
        val g = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault())
        val gy = g.get(Calendar.YEAR)
        val gm = g.get(Calendar.MONTH) + 1
        val gd = g.get(Calendar.DAY_OF_MONTH)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val j = gregorianToJalali(gy, gm, gd)
        return "${j[0]}/${"%02d".format(j[1])}/${"%02d".format(j[2])} $time"
    }

    private fun gregorianToJalali(gy:Int, gm:Int, gd:Int): IntArray {
        // simple, widely used algorithm
        val g_d_m = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var jy: Int
        var jm: Int
        var jd: Int
        var gy2 = gy - 1600
        var gm2 = gm - 1
        var gd2 = gd - 1
        var g_day_no = 365*gy2 + (gy2+3)/4 - (gy2+99)/100 + (gy2+399)/400
        g_day_no += g_d_m[gm2] + gd2
        if (gm2>1 && ((gy%4==0 && gy%100!=0) || (gy%400==0))) g_day_no += 1
        var j_day_no = g_day_no - 79
        val j_np = j_day_no/12053
        j_day_no %= 12053
        jy = 979 + 33*j_np + 4*(j_day_no/1461)
        j_day_no %= 1461
        if (j_day_no>=366) {
            jy += (j_day_no-366)/365
            j_day_no = (j_day_no-366)%365
        }
        val j_month_days = intArrayOf(31,31,31,31,31,31,30,30,30,30,30,29)
        var i = 0
        while (i<11 && j_day_no>=j_month_days[i]) {
            j_day_no -= j_month_days[i]
            i++
        }
        jm = i+1
        jd = j_day_no+1
        return intArrayOf(jy, jm, jd)
    }
}
