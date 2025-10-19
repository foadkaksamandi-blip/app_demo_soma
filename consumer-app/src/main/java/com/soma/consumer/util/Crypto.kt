package com.soma.consumer.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object Crypto {
    private const val KS = "AndroidKeyStore"
    private const val ALGO = KeyProperties.KEY_ALGORITHM_EC
    private const val SIG = "SHA256withECDSA"
    private const val ALIAS = "SOMA_CONSUMER_SIGNING"

    fun ensureKeypair(): KeyPair {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        if (ks.containsAlias(ALIAS)) {
            val priv = ks.getKey(ALIAS, null) as java.security.PrivateKey
            val pub = ks.getCertificate(ALIAS).publicKey
            return KeyPair(pub, priv)
        }
        val kpg = KeyPairGenerator.getInstance(ALGO, KS)
        kpg.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .build()
        )
        return kpg.generateKeyPair()
    }

    fun publicKeyX509(): String {
        val kp = ensureKeypair()
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    fun sign(canonical: String): String {
        val kp = ensureKeypair()
        val s = Signature.getInstance(SIG)
        s.initSign(kp.private)
        s.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(s.sign(), Base64.NO_WRAP)
    }

    fun verify(pubKeyX509Base64: String, canonical: String, sigBase64: String): Boolean {
        val kf = KeyFactory.getInstance(ALGO)
        val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubKeyX509Base64, Base64.NO_WRAP)))
        val s = Signature.getInstance(SIG)
        s.initVerify(pub)
        s.update(canonical.toByteArray(Charsets.UTF_8))
        return s.verify(Base64.decode(sigBase64, Base64.NO_WRAP))
    }

    // Canonical strings
    fun canonicalPayReq(merchantId: String, walletType: String, amount: Long, ts: Long, nonce: String) =
        "$merchantId|$walletType|$amount|$ts|$nonce"

    fun canonicalAck(merchantId: String, walletType: String, amount: Long, txId: String, ts: Long) =
        "$merchantId|$walletType|$amount|$txId|$ts"
}
