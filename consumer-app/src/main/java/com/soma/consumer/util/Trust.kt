package com.soma.consumer.util

import android.content.Context
import org.json.JSONObject

class TrustStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("consumer", Context.MODE_PRIVATE)
    private val KEY_TRUST = "trusted_merchants" // map: merchantId -> pubkey (Base64)

    fun put(merchantId: String, pubKey: String) {
        val map = JSONObject(sp.getString(KEY_TRUST, "{}"))
        map.put(merchantId, pubKey)
        sp.edit().putString(KEY_TRUST, map.toString()).apply()
    }

    fun get(merchantId: String): String? {
        val map = JSONObject(sp.getString(KEY_TRUST, "{}"))
        return if (map.has(merchantId)) map.getString(merchantId) else null
    }
}
