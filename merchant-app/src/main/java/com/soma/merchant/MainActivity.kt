package com.soma.merchant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soma.merchant.databinding.ActivityMainBinding
import shared.utils.DateUtils   // ← مسیر صحیح کتابخانهٔ تاریخ شمسی

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // نمایش تاریخ شمسی در لاگ یا روی UI
        val now = DateUtils.nowJalaliDateTime()
        println("📅 تاریخ شمسی فعلی: $now")
    }
}
