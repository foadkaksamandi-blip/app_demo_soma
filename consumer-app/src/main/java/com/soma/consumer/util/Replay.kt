package com.soma.consumer.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class ReplayProtector(ctx: Context) {
    private val sp = ctx.getSharedPreferences("consumer", Context.MODE_PRIVATE)
    private val KEY_USED = "replay_used"
    private val WINDOW_MS = 5 * 60 * 1000L // 5 دقیقه

    private fun load(): JSONArray = JSONArray(sp.getString(KEY_USED, "[]"))
    private fun save(arr: JSONArray) { sp.edit().putString(KEY_USED, arr.toString()).apply() }

    fun isFreshAndMark(key: String, ts: Long): Boolean {
        val now = System.currentTimeMillis()
        if (abs(now - ts) > WINDOW_MS) return false
        val arr = load()
        val keep = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (now - o.getLong("ts") < WINDOW_MS) keep.put(o)
        }
        for (i in 0 until keep.length()) {
            if (keep.getJSONObject(i).getString("k") == key) return false
        }
        keep.put(JSONObject().put("k", key).put("ts", now))
        save(keep)
        return true
    }
}
