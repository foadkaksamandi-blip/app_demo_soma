package com.soma.Merchant.ble // در merchant همین کلاس با package com.soma.merchant.ble

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object BtPerms {
    fun ensure(activity: Activity) {
        val req = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).forEach { p ->
                if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) req += p
            }
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).forEach { p ->
                if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) req += p
            }
        }
        if (req.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, req.toTypedArray(), 8010)
        }
    }
}
