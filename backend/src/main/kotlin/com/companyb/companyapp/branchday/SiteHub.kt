package com.companyb.companyapp.branchday

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What a branch's members need to share a day through a site hub (`hub/`): the room the relay files
 * their records under and the key that seals them. Both come from the deployment's secret, so every
 * member gets the same pair for the same branch and day with nothing stored, and the relay, which
 * sees only the room, can't read what it carries. Also seals server-stored hub keys and hashes
 * revocable laptop tokens.
 */
object SiteHub {
    private const val KEY_BYTES = 32
    private const val TOKEN_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val HMAC = "HmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val LAPTOP_PREFIX = "hl2."

    private val random = SecureRandom()

    @Volatile
    private var secret: ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    /** [value] is `JWT_SECRET`; never in git. */
    fun configure(value: String) {
        secret = mac(value.toByteArray(), "site-hub")
    }

    data class Ticket(
        val room: String,
        val key: String,
        val date: LocalDate,
    )

    fun ticket(
        branchId: UUID,
        date: LocalDate,
    ): Ticket {
        val id = "$branchId|$date"
        return Ticket(
            room = mac(secret, "room|$id").joinToString("") { "%02x".format(it) },
            key = Base64.getUrlEncoder().withoutPadding().encodeToString(mac(secret, "key|$id")),
            date = date,
        )
    }

    /** Generates a fresh opaque laptop token; only its SHA-256 hash is stored in Postgres. */
    fun newLaptopToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return LAPTOP_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Lowercase 64-char SHA-256 hex digest of [token]. */
    fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Seals [plaintext] with AES-256-GCM under a purpose-bound key derived from the deployment secret. */
    fun seal(
        purpose: String,
        plaintext: ByteArray,
    ): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { random.nextBytes(it) }
        val keySpec = SecretKeySpec(mac(secret, "seal|$purpose"), "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(purpose.toByteArray())
        return iv + cipher.doFinal(plaintext)
    }

    /** Opens a blob produced by [seal] for the same [purpose]. */
    fun unseal(
        purpose: String,
        blob: ByteArray,
    ): ByteArray {
        require(blob.size > GCM_IV_BYTES) { "sealed blob too short" }
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val keySpec = SecretKeySpec(mac(secret, "seal|$purpose"), "AES")
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(purpose.toByteArray())
        return cipher.doFinal(ciphertext)
    }

    private fun mac(
        key: ByteArray,
        data: String,
    ): ByteArray = Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(data.toByteArray())
}
