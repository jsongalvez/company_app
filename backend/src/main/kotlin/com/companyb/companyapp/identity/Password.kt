package com.companyb.companyapp.identity

import at.favre.lib.crypto.bcrypt.BCrypt

object Password {
    private const val BCRYPT_COST = 12
    private const val MAX_PASSWORD_BYTES = 72

    private var dummyHash: String = ""

    fun init(authDummyPassword: String) {
        dummyHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, authDummyPassword.toCharArray())
    }

    fun create(raw: String): String {
        require(raw.encodeToByteArray().size <= MAX_PASSWORD_BYTES) {
            "password must not be longer than $MAX_PASSWORD_BYTES bytes"
        }
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, raw.toCharArray())
    }

    @Suppress("ReturnCount", "UnreachableCode")
    fun verify(
        raw: String,
        hash: String?,
    ): Boolean {
        if (raw.encodeToByteArray().size > MAX_PASSWORD_BYTES) return false
        val passwordHash = hash ?: dummyHash
        val result =
            runCatching {
                BCrypt.verifyer().verify(raw.toCharArray(), passwordHash)
            }.getOrNull() ?: return false
        return hash != null && result.verified
    }
}
