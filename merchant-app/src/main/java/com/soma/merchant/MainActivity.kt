package com.soma.merchant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soma.merchant.databinding.ActivityMainBinding
import shared.utils.DateUtils   // مسیر صحیح برای تاریخ شمسی
import com.soma.merchant.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // نمایش تاریخ شمسی روی صفحه
        val now = DateUtils.nowJalaliDateTime()
        binding.root.post {
            println("📅 تاریخ شمسی فعلی: $now")
        }
    }
}
