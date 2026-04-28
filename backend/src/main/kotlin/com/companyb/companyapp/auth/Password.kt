package com.companyb.companyapp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.dotenv
import com.companyb.companyapp.validation.PasswordPolicy

object Password {
    private val BCRYPT_COST = 12
    private val DUMMY_HASH: String by lazy {
        BCrypt.withDefaults().hashToString(BCRYPT_COST, dotenv["AUTH_DUMMY_PASSWORD"].toCharArray())
    }

    fun create(raw: String): Result {
        if (PasswordPolicy.isValid(raw)) {
            return Result.FAILURE(RegisterResult.WeakPassword(PasswordPolicy.MIN_LENGTH))
        }

        val passwordHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, raw.toCharArray())
        return Result.SUCCESS(passwordHash)
    }

    fun verify(
        raw: String,
        hash: String?,
    ): Boolean {
        val passwordHash = hash ?: DUMMY_HASH
        val result = BCrypt.verifyer().verify(raw.toCharArray(), passwordHash)
        return hash != null && result.verified
    }

    sealed class Result {
        data class SUCCESS(
            val passwordHash: String,
        ) : Result()

        data class FAILURE(
            val reason: RegisterResult,
        ) : Result()
    }
}
