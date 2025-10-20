package com.soma.consumer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import shared.utils.DateUtils

data class Tx(val amount: Long, val type: String, val wallet: String, val time: Long)

class TxStore(private val ctx: Context) {
    private val PREF = "soma_consumer_store"
    private val KEY_TXS = "tx_history"
    private val KEY_BAL_PREFIX = "bal_"

    private val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun add(amount: Long, type: String, wallet: String) {
        val arr = JSONArray(sp.getString(KEY_TXS, "[]"))
        val obj = JSONObject()
        val ts = System.currentTimeMillis()
        obj.put("amount", amount)
        obj.put("type", type)
        obj.put("wallet", wallet)
        obj.put("time", ts)
        arr.put(obj)
        sp.edit().putString(KEY_TXS, arr.toString()).apply()
    }

    fun list(): List<Tx> {
        val raw = sp.getString(KEY_TXS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val res = mutableListOf<Tx>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            res.add(Tx(o.getLong("amount"), o.getString("type"), o.getString("wallet"), o.getLong("time")))
        }
        return res
    }

    fun balance(wallet: String): Long {
        return sp.getLong(KEY_BAL_PREFIX + wallet, when (wallet) {
            "اصلی" -> 10_000_000L
            "رمزارز ملی" -> 0L
            "یارانه ملی" -> 0L
            "اعتبار اضطراری ملی" -> 0L
            else -> 0L
        })
    }

    fun setBalance(wallet: String, v: Long) {
        sp.edit().putLong(KEY_BAL_PREFIX + wallet, v).apply()
    }
}
