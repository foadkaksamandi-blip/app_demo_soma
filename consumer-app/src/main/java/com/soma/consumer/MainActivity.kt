package com.soma.consumer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.soma.consumer.ble.BleClient
import com.soma.consumer.ble.BtPerms
import kotlinx.coroutines.launch

/**
 * اپ خریدار – پرداخت از طریق BLE و QR (حالت سبز + BLE واقعی)
 */
class MainActivity : ComponentActivity() {

    private var balance by mutableStateOf(10_000_000L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BtPerms.ensure(this) // گرفتن مجوز BLE
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BuyerScreen()
                }
            }
        }
    }

    @Composable
    fun BuyerScreen() {
        var amountInput by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("آپ آفلاین سوما 👤", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("اپ خریدار", style = MaterialTheme.typography.titleMedium)
            Text("موجودی: ${"%,d".format(balance)} تومان", fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                decorationBox = { inner -> 
                    Box(
                        Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) { inner() }
                }
            )

            // پرداخت از طریق بلوتوث
            Button(onClick = {
                val amt = amountInput.toLongOrNull() ?: 0L
                if (amt > 0 && amt <= balance) {
                    lifecycleScope.launch {
                        status = "در حال اتصال..."
                        val ok = BleClient.pay(this@MainActivity, amt, "اصلی")
                        if (ok) {
                            balance -= amt
                            status = "پرداخت انجام شد ✅"
                        } else {
                            status = "اتصال یا پرداخت ناموفق ❌"
                        }
                    }
                } else status = "مبلغ معتبر نیست"
            }) {
                Text("پرداخت با بلوتوث")
            }

            // پرداخت از طریق QR (کد موجود قبلی شما دست‌نخورده باقی می‌ماند)
            Button(onClick = {
                val intent = Intent(this@MainActivity, QrPaymentActivity::class.java)
                startActivity(intent)
            }) {
                Text("پرداخت با QR کد")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(status)
        }
    }
}
