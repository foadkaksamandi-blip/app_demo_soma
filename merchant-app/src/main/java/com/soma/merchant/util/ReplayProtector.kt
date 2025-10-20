package com.soma.merchant.util

object ReplayProtector {
    private var lastNonce: String? = null
    private var lastAt: Long = 0L
    private const val WINDOW_MS = 10_000L

    @Synchronized
    fun acceptOnce(nonce: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nonce.isBlank()) return false
        val ok = if (nonce == lastNonce && nowMs - lastAt <= WINDOW_MS) {
            false
        } else {
            lastNonce = nonce
            lastAt = nowMs
            true
        }
        return ok
    }

    @Synchronized
    fun reset() {
        lastNonce = null
        lastAt = 0L
    }
}
