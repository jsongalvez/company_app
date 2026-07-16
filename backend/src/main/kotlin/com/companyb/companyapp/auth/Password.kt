package com.companyb.companyapp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.companyb.companyapp.database.dotenv

object Password {
    private const val BCRYPT_COST = 12
    private const val MAX_PASSWORD_BYTES = 72

    private val DUMMY_HASH: String by lazy {
        BCrypt.withDefaults().hashToString(BCRYPT_COST, dotenv["AUTH_DUMMY_PASSWORD"].toCharArray())
    }

    fun create(raw: String): String {
        require(raw.encodeToByteArray().size <= MAX_PASSWORD_BYTES) {
            "password must not be longer than $MAX_PASSWORD_BYTES bytes"
        }
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, raw.toCharArray())
    }

    @Suppress("ReturnCount")
    fun verify(
        raw: String,
        hash: String?,
    ): Boolean {
        if (raw.encodeToByteArray().size > MAX_PASSWORD_BYTES) return false
        val passwordHash = hash ?: DUMMY_HASH
        val result =
            runCatching {
                BCrypt.verifyer().verify(raw.toCharArray(), passwordHash)
            }.getOrNull() ?: return false
        return hash != null && result.verified
    }
}
