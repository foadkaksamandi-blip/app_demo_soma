package com.soma.consumer.data

import android.content.Context
import com.soma.consumer.util.Jalali
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class WalletType { MAIN, CBDC, SUBSIDY, EMERGENCY }

data class TxRec(
    val id: String,
    val wallet: WalletType,
    val amount: Long,
    val type: String,     // e.g. "پرداخت QR", "پرداخت BLE", ...
    val time: String      // Jalali timestamp
)

class TxStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("consumer", Context.MODE_PRIVATE)

    private fun keyBal(w: WalletType) = "balance_${w.name}"
    private val KEY_TXS = "txs" // JSON Array of tx objects
    private val KEY_INIT = "init_balances"

    /** موجودی اولیه خریدار روی هر کیف (تومان) */
    private val initial = mapOf(
        WalletType.MAIN to 10_000_000L,
        WalletType.CBDC to 0L,
        WalletType.SUBSIDY to 0L,
        WalletType.EMERGENCY to 0L
    )

    init { ensureInit() }

    private fun ensureInit() {
        if (!sp.getBoolean(KEY_INIT, false)) {
            val e = sp.edit()
            initial.forEach { (w, v) -> e.putLong(keyBal(w), v) }
            e.putString(KEY_TXS, "[]")
            e.putBoolean(KEY_INIT, true)
            e.apply()
        }
    }

    fun balance(wallet: WalletType): Long = sp.getLong(keyBal(wallet), 0L)

    fun setBalance(wallet: WalletType, value: Long) {
        sp.edit().putLong(keyBal(wallet), value).apply()
    }

    fun add(amount: Long, type: String, wallet: WalletType): TxRec {
        val arr = JSONArray(sp.getString(KEY_TXS, "[]"))
        val rec = JSONObject()
        val id = "c-" + UUID.randomUUID().toString().take(8)
        val t = TxRec(id, wallet, amount, type, Jalali.now())
        rec.put("id", t.id)
        rec.put("wallet", t.wallet.name)
        rec.put("amount", t.amount)
        rec.put("type", t.type)
        rec.put("time", t.time)
        arr.put(rec)
        sp.edit().putString(KEY_TXS, arr.toString()).apply()
        return t
    }

    fun list(): List<TxRec> {
        val arr = JSONArray(sp.getString(KEY_TXS, "[]"))
        val out = mutableListOf<TxRec>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                TxRec(
                    id = o.getString("id"),
                    wallet = WalletType.valueOf(o.getString("wallet")),
                    amount = o.getLong("amount"),
                    type = o.getString("type"),
                    time = o.getString("time")
                )
            )
        }
        return out
    }
}
