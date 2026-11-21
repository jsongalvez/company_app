package com.companyb.companyapp.branchday

import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What a branch's members need to share a day through a site hub (`hub/`): the room the relay files
 * their records under and the key that seals them. Both come from the deployment's secret, so every
 * member gets the same pair for the same branch and day with nothing stored, and the relay, which
 * sees only the room, can't read what it carries.
 */
object SiteHub {
    private const val KEY_BYTES = 32
    private const val HMAC = "HmacSHA256"

    @Volatile
    private var secret: ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

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

    private fun mac(
        key: ByteArray,
        data: String,
    ): ByteArray = Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(data.toByteArray())
}
