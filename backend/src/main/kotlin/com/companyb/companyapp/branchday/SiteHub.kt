package com.companyb.companyapp.branchday

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
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

    /** How long a laptop token lasts; each certificate renewal hands out a fresh one. */
    val LAPTOP_TOKEN_LIFE: Duration = Duration.ofDays(LAPTOP_TOKEN_DAYS)
    private const val LAPTOP_TOKEN_DAYS = 365L
    private const val LAPTOP_PREFIX = "hl1"

    data class LaptopToken(
        val token: String,
        val expiresAt: Instant,
    )

    /** A token naming [laptopId] until a year from [now], signed with the deployment's secret: nothing stored. */
    fun laptopToken(
        laptopId: UUID,
        now: Instant,
    ): LaptopToken {
        val exp = now.plus(LAPTOP_TOKEN_LIFE).epochSecond
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac(secret, "laptop|$laptopId|$exp"))
        return LaptopToken("$LAPTOP_PREFIX.$laptopId.$exp.$sig", Instant.ofEpochSecond(exp))
    }

    /** The laptop a live, untampered token names, else null. */
    fun laptop(
        token: String,
        now: Instant,
    ): UUID? {
        val parts = token.split('.').takeIf { it.size == LAPTOP_PARTS && it[0] == LAPTOP_PREFIX }
        val id = parts?.let { runCatching { UUID.fromString(it[1]) }.getOrNull() }
        val exp = parts?.get(2)?.toLongOrNull()
        val got = parts?.let { runCatching { Base64.getUrlDecoder().decode(it[SIG_PART]) }.getOrNull() }
        val live = id != null && exp != null && got != null && now.epochSecond < exp
        return id.takeIf { live && MessageDigest.isEqual(mac(secret, "laptop|$id|$exp"), got) }
    }

    private const val LAPTOP_PARTS = 4
    private const val SIG_PART = 3

    private fun mac(
        key: ByteArray,
        data: String,
    ): ByteArray = Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(data.toByteArray())
}
