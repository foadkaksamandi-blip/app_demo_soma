package com.soma.merchant.util

import android.content.Context

class ReplayProtector(private val ctx: Context) {
    private val PREF = "soma_replay"
    private val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val PREFIX = "r_"

    // اگر id جدید است (قبلاً دیده نشده) -> ثبت و true برمی‌گردد
    fun isFreshAndMark(id: String, nowMs: Long, ttlMs: Long = 24L * 3600L * 1000L): Boolean {
        val prev = sp.getLong(PREFIX + id, 0L)
        if (prev == 0L) {
            sp.edit().putLong(PREFIX + id, nowMs).apply()
            return true
        }
        // اگر قبلاً ثبت شده ولی خیلی قدیم است، باز هم اجازه می‌دهیم و ریست می‌کنیم
        if (nowMs - prev > ttlMs) {
            sp.edit().putLong(PREFIX + id, nowMs).apply()
            return true
        }
        return false
    }
}
