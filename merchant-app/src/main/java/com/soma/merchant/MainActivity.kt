package com.soma.merchant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.soma.merchant.ble.BlePeripheralService
import com.soma.merchant.ble.BtPerms
import kotlinx.coroutines.launch

/**
 * اپ فروشنده – فعال‌سازی BLE سرویس و بروزرسانی موجودی هنگام تراکنش
 */
class MainActivity : ComponentActivity() {

    private var balance = 5_000_000L
    private lateinit var statusView: TextView
    private lateinit var balanceView: TextView

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BlePeripheralService.ACTION_INCOMING_PAYMENT) {
                val amount = intent.getStringExtra(BlePeripheralService.EXTRA_AMOUNT)?.toLongOrNull() ?: 0L
                if (amount > 0) {
                    balance += amount
                    runOnUiThread {
                        balanceView.text = "موجودی: ${"%,d".format(balance)} تومان"
                        statusView.text = "دریافت موفق ✅ +${"%,d".format(amount)}"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        balanceView = findViewById(R.id.balanceView)
        statusView = findViewById(R.id.statusView)
        balanceView.text = "موجودی: ${"%,d".format(balance)} تومان"

        BtPerms.ensure(this)
        startForegroundService(Intent(this, BlePeripheralService::class.java))

        registerReceiver(rx, IntentFilter(BlePeripheralService.ACTION_INCOMING_PAYMENT))

        // سایر دکمه‌های شما (QR و …) بدون تغییر قبلی
        lifecycleScope.launch {
            statusView.text = "BLE آماده دریافت 💚"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(rx)
    }
}
