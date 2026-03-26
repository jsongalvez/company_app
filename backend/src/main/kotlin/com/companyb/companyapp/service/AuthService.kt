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

        val result: BCrypt.Result = BCrypt.verifyer().verify(password.toCharArray(), appUser?.passwordHash)
        if (result.verified) {
            logger.info { "[LOGIN] Successfully verified user" }
        } else {
            logger.warn { "[LOGIN] Failed to verify user" }
        }

        if (appUser == null || !result.verified) {
            return null
        }

        logger.info { "[LOGIN] Generating token for ${appUser.id}" }
        val token: String = JwtService.generateToken(appUser.id)
        logger.info { "[LOGIN] Successfully generated token for ${appUser.id}" }
        return token
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
