package com.companyb.companyapp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.companyb.companyapp.dotenv

object Password {
    private const val BCRYPT_COST = 12
    private val DUMMY_HASH: String by lazy {
        BCrypt.withDefaults().hashToString(BCRYPT_COST, dotenv["AUTH_DUMMY_PASSWORD"].toCharArray())
    }

    fun create(raw: String): String {
        val passwordHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, raw.toCharArray())
        return passwordHash
    }

    fun verify(
        raw: String,
        hash: String?,
    ): Boolean {
        val passwordHash = hash ?: DUMMY_HASH
        val result = BCrypt.verifyer().verify(raw.toCharArray(), passwordHash)
        return hash != null && result.verified
    }
}
