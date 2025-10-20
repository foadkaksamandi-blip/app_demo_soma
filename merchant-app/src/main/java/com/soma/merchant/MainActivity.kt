package com.soma.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soma.merchant.ui.SomaMerchantTheme
import shared.utils.DateUtils

// ⚠️ اگر این ایمپورت‌ها در نسخه قبلی بودند، حتماً حذف شده باشند:
// import com.soma.merchant.databinding.ActivityMainBinding
// import androidx.databinding.*
// setContentView(...)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SomaMerchantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MerchantHome()
                }
            }
        }
    }
}

@Composable
private fun MerchantHome() {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = "نسخه دمو فروشنده سوما",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "اکنون (شمسی): ${DateUtils.nowJalaliDateTime()}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
