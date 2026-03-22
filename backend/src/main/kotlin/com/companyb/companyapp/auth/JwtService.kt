package com.companyb.companyapp.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.companyb.companyapp.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object JwtService {
    private val logger = KotlinLogging.logger {}
    private val algorithm =
        run {
            val secret = dotenv["JWT_SECRET"]
            require(secret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
            Algorithm.HMAC256(secret)
        }

    fun generateToken(userId: String): String {
        logger.info { "[GENERATE-TOKEN] Generating token for $userId" }
        val expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
        logger.info { "[GENERATE-TOKEN] Token expires at $expiresAt" }
        val token =
            JWT
                .create()
                .withSubject(userId)
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm)
        logger.info { "[GENERATE-TOKEN] Successfully generated token" }
        return token
    }

    fun verifyToken(token: String): String? =
        try {
            logger.info { "[VERIFY-TOKEN] Verifying token" }
            val subj =
                JWT
                    .require(algorithm)
                    .build()
                    .verify(token)
                    .subject
            logger.info { "[VERIFY-TOKEN] Successfully verified token" }
            subj
        } catch (e: JWTVerificationException) {
            logger.warn { "[VERIFY-TOKEN] Invalid token: ${e.message}" }
            null
        }
}
