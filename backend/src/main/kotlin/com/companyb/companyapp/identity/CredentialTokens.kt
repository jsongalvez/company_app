package com.companyb.companyapp.identity

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Raw-token generation and hashing for `credential_token` rows (#350). The raw code exists
 * only in the minted link/response and in the invitee's possession; the database stores its
 * SHA-256 digest, so a database leak cannot expose live links.
 */
object CredentialTokens {
    private const val RAW_BYTES = 32
    private const val HASH_ALGORITHM = "SHA-256"

    private val random = SecureRandom()

    /** URL-safe 256-bit random token (~43 chars, no padding). */
    fun generate(): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(RAW_BYTES).also(random::nextBytes))

    /** Lowercase hex SHA-256 digest of [raw] — the value stored in `credential_token.token_hash`. */
    fun hash(raw: String): String =
        MessageDigest
            .getInstance(HASH_ALGORITHM)
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
