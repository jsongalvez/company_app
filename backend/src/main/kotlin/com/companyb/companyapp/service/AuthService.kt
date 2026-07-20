package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.auth.RateLimiter
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.validation.EmailPolicy
import com.companyb.companyapp.validation.PasswordPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object AuthService {
    private val logger = KotlinLogging.logger { }

    @Suppress("ReturnCount")
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
        DenyList.allow(UUID.fromString(appUser.id)) // safe: appUser is null-checked above, .id is non-null String
        logger.info { "[LOGIN] User has logged in successfully " }
        return LoginResult.Success(token)
    }

    @Suppress("ReturnCount")
    fun register(
        username: String,
        password: String,
        email: String,
        displayName: String,
    ): RegisterResult {
        logger.info { "[REGISTER] User attempts to register" }
        val appUser: AppUser? = UserRepository.findByUsername(username)
        if (appUser != null) {
            logger.info { "[REGISTER] Username $username is taken" }
            return RegisterResult.UsernameTaken
        }

        if (!PasswordPolicy.isValid(password)) {
            return RegisterResult.WeakPassword(PasswordPolicy.MIN_LENGTH)
        }

        if (!EmailPolicy.isValid(email)) {
            return RegisterResult.InvalidEmail
        }

        if (UserRepository.isEmailTaken(email)) {
            return RegisterResult.EmailTaken
        }

        val passwordHash = Password.create(password)

        val userID: UUID = UserRepository.createUser(username, passwordHash, email, displayName)
        logger.info { "[REGISTER] Registered user ${userID.toString().maskUUID()} successfully" }
        return RegisterResult.Success
    }
}
