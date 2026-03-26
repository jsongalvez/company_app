package com.companyb.companyapp.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import io.github.oshai.kotlinlogging.KotlinLogging

object AuthService {
    private val logger = KotlinLogging.logger { }

    fun login(
        username: String,
        password: String,
    ): String? {
        logger.info { "[LOGIN] Verifying user login" }
        val appUser: AppUser? = UserRepository.findByUsername(username)

        val dummyHash = "hC@qxPuS6$#je69nGaNgLPwJpR!T!q&&T7Q2*&o28Y&4Aj#PkR%kccLHCiQj" // run bcrypt on login failure
        val passwordHash = appUser?.passwordHash ?: dummyHash
        val result: BCrypt.Result = BCrypt.verifyer().verify(password.toCharArray(), passwordHash)

        if (appUser == null || !result.verified) {
            return null.also { logger.warn { "[LOGIN] Failed login attempt" } }
        }

        val token: String = JwtService.generateToken(appUser.id)
        return token.also { logger.info { "[LOGIN] User has logged in successfully " } }
    }

    fun register(
        username: String,
        password: String,
    ): Boolean {
        logger.info { "[REGISTER] User attempts to register" }
        val appUser: AppUser? = UserRepository.findByUsername(username)
        if (appUser != null) {
            logger.info { "[REGISTER] Username $username is taken" }
            return false
        }

        logger.info { "[REGISTER] Creating password hash for user $username" }
        val cost = 12
        val passwordHash: String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())
        logger.info { "[REGISTER] Password hash created for user $username" }

        UserRepository.createUser(username, passwordHash)
        logger.info { "[REGISTER] Registered user $username successfully" }
        return true
    }
}
