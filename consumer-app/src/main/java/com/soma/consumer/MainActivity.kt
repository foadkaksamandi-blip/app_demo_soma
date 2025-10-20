package com.soma.consumer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.soma.consumer.ble.BleClient

class MainActivity : AppCompatActivity() {

    private lateinit var bleClient: BleClient
    private lateinit var txtBalance: TextView
    private lateinit var txtAmount: EditText
    private lateinit var btnQrPay: Button
    private lateinit var btnBlePay: Button
    private lateinit var imgQr: ImageView
    private var balance = 10000000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleClient = BleClient(this)
        txtBalance = findViewById(R.id.txtBalance)
        txtAmount = findViewById(R.id.txtAmount)
        btnQrPay = findViewById(R.id.btnQrPay)
        btnBlePay = findViewById(R.id.btnBlePay)
        imgQr = findViewById(R.id.imgQr)

        txtBalance.text = "موجودی: ${balance} تومان"

        btnQrPay.setOnClickListener {
            val amountText = txtAmount.text.toString()
            if (amountText.isEmpty()) {
                Toast.makeText(this, "مبلغ خرید را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toInt()
            if (amount > balance) {
                Toast.makeText(this, "موجودی کافی نیست", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val qrData = "PAY:${amount}"
            val qrBitmap = generateQrBitmap(qrData)
            imgQr.setImageBitmap(qrBitmap)
            Toast.makeText(this, "کد QR پرداخت تولید شد", Toast.LENGTH_SHORT).show()
        }

        btnBlePay.setOnClickListener {
            val amountText = txtAmount.text.toString()
            if (amountText.isEmpty()) {
                Toast.makeText(this, "مبلغ خرید را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountText.toInt()
            if (amount > balance) {
                Toast.makeText(this, "موجودی کافی نیست", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startBleTransaction(amount)
        }

        checkBluetoothPermissions()
    }

    private fun startBleTransaction(amount: Int) {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "بلوتوث فعال نیست", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "هیچ دستگاهی پیدا نشد", Toast.LENGTH_SHORT).show()
            return
        }

        val device = pairedDevices.first()
        bleClient.connectToDevice(device) { success ->
            runOnUiThread {
                if (success) {
                    balance -= amount
                    txtBalance.text = "موجودی: ${balance} تومان"
                    Toast.makeText(this, "پرداخت موفق با بلوتوث ✅", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "اتصال بلوتوث برقرار نشد ❌", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateQrBitmap(data: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    }

    private fun checkBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        ActivityCompat.requestPermissions(this, permissions, 1)
    }
}
