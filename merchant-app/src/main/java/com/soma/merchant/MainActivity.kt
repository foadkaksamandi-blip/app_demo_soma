package com.soma.merchant

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.soma.merchant.R
import com.soma.merchant.ble.BlePeripheralService
import com.soma.merchant.data.TxStore
import shared.utils.DateUtils

class MainActivity : AppCompatActivity() {

    // رنگ‌ها
    private val GreenBg = android.graphics.Color.parseColor("#059669")
    private val BtnPurple = android.graphics.Color.parseColor("#7C3AED")
    private val BtnAmber = android.graphics.Color.parseColor("#F59E0B")
    private val BtnBlue = android.graphics.Color.parseColor("#2563EB")
    private val BtnPink = android.graphics.Color.parseColor("#EC4899")
    private val WhiteText = android.graphics.Color.WHITE

    // Store
    private lateinit var store: TxStore

    // UI
    private lateinit var txtNow: TextView
    private lateinit var txtBalance: TextView
    private lateinit var edtAmount: EditText
    private lateinit var btnScanProof: Button
    private lateinit var btnHistory: Button
    private lateinit var imgQr: ImageView

    // وضعیت فعال کیف پول
    private var activeWallet: String = "اصلی"

    // هندل زمان شمسی
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            try {
                txtNow.text = "تاریخ و ساعت: " + DateUtils.nowJalaliDateTime()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 1000)
        }
    }

    // ریسیور پرداخت ورودی از BLE
    private val bleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BlePeripheralService.ACTION_INCOMING_PAYMENT) {
                val amount = intent.getStringExtra(BlePeripheralService.EXTRA_AMOUNT) ?: "0"
                val wallet = intent.getStringExtra(BlePeripheralService.EXTRA_WALLET) ?: "اصلی"
                try {
                    val now = DateUtils.nowJalaliDateTime()
                    Toast.makeText(
                        this@MainActivity,
                        "💰 پرداخت جدید: مبلغ $amount از کیف $wallet در $now",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) { }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // اجزای UI
        txtNow = findViewById(R.id.txtNow)
        txtBalance = findViewById(R.id.txtBalance)
        edtAmount = findViewById(R.id.edtAmount)
        btnScanProof = findViewById(R.id.btnScanProof)
        btnHistory = findViewById(R.id.btnHistory)
        imgQr = findViewById(R.id.imgQr)

        // شروع نمایش زمان شمسی
        handler.post(timeRunnable)

        // شروع سرویس BLE برای فروشنده
        ContextCompat.startForegroundService(
            this,
            Intent(this, BlePeripheralService::class.java)
        )

        // ثبت ریسیور پرداخت
        registerReceiver(
            bleReceiver,
            IntentFilter(BlePeripheralService.ACTION_INCOMING_PAYMENT)
        )

        // درخواست مجوزهای بلوتوث
        requestBlePermissions()

        // رویداد تستی دکمه‌ها
        btnScanProof.setOnClickListener {
            Toast.makeText(this, "📶 در حال اسکن...", Toast.LENGTH_SHORT).show()
        }

        btnHistory.setOnClickListener {
            Toast.makeText(this, "📜 تاریخچه تراکنش‌ها", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBlePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(bleReceiver)
            handler.removeCallbacks(timeRunnable)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
