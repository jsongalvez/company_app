package com.companyb.companyapp.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.auth.RateLimiter
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object AuthService {
    private val logger = KotlinLogging.logger { }

    fun login(
        username: String,
        password: String,
        ip: String,
    ): LoginResult {
        logger.info { "[LOGIN] Login attempt for $username from $ip" }

        if (!RateLimiter.isAllowed(ip)) {
            logger.warn { "[LOGIN] Rate limiting $ip" }
            return LoginResult.RateLimited
        }

        logger.info { "[LOGIN] Verifying user login" }
        val appUser: AppUser? = UserRepository.findByUsername(username)

        val isVerified = Password.verify(password, appUser?.passwordHash)

        if (appUser == null || !isVerified) {
            logger.warn { "[LOGIN] Failed login attempt for user: $username" }
            return LoginResult.InvalidCredentials
        }

        val token: String = JwtService.generateToken(appUser.id)
        logger.info { "[LOGIN] User has logged in successfully " }
        return LoginResult.Success(token)
    }

    @Suppress("ReturnCount")
    fun register(
        username: String,
        password: String,
    ): RegisterResult {
        logger.info { "[REGISTER] User attempts to register" }
        val appUser: AppUser? = UserRepository.findByUsername(username)
        if (appUser != null) {
            logger.info { "[REGISTER] Username $username is taken" }
            return RegisterResult.UsernameTaken
        }

        val minimumPasswordLength = 8
        if (password.length < minimumPasswordLength) {
            logger.info { "[REGISTER] Password does not match requirements" }
            return RegisterResult.WeakPassword(minimumPasswordLength)
        }

        logger.info { "[REGISTER] Creating password hash for user" }
        val cost = 12
        val passwordHash: String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())
        logger.info { "[REGISTER] Password hash created for user" }

        val userID: UUID = UserRepository.createUser(username, passwordHash)
        logger.info { "[REGISTER] Registered user ${userID.toString().maskUUID()} successfully" }
        return RegisterResult.Success
    }
}
