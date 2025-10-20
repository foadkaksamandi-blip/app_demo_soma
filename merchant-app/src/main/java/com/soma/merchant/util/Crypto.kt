package com.soma.merchant.util

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Crypto {
    // Demo key (local). در محیط واقعی از HSM/کلید امن استفاده شود.
    private const val KEY = "soma_demo_secret_key_please_change"

    fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val sig = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }
}
