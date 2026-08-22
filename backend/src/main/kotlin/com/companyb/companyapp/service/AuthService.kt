package com.companyb.companyapp.service

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.auth.RateLimiter
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import io.github.oshai.kotlinlogging.KotlinLogging

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
}
