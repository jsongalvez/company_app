package com.companyb.companyapp.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.companyb.companyapp.dotenv
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.time.Duration.Companion.seconds

object JwtService {
    private val logger = KotlinLogging.logger {}
    private val issuer =
        run {
            val issuer = dotenv["JWT_ISSUER"]
            require(!issuer.isNullOrBlank()) { "JWT_ISSUER must be set" }
            issuer
        }
    private val audience =
        run {
            val audience = dotenv["JWT_AUDIENCE"]
            require(!audience.isNullOrBlank()) { "JWT_AUDIENCE must be set" }
            audience
        }
    private val algorithm =
        run {
            val secret = dotenv["JWT_SECRET"]
            require(!secret.isNullOrBlank()) { "JWT_SECRET must be set" }
            require(secret.length >= 32) { "JWT_SECRET must be at least 32 characters" }
            // TODO: Consider RS256 for offline login
            Algorithm.HMAC256(secret)
        }

    fun generateToken(userId: String): String {
        logger.info { "[GENERATE-TOKEN] Generating token for ${userId.maskUUID()}" }
        // TODO: Reduce token lifetime to 15 minutes (need refresh token)
        val now = Instant.now()
        val expiresAt = now.plus(1, ChronoUnit.DAYS)
        logger.info { "[GENERATE-TOKEN] Token expires at $expiresAt" }
        val token =
            JWT
                .create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withSubject(userId)
                .withExpiresAt(Date.from(expiresAt))
                .withIssuedAt(Date.from(now))
                .sign(algorithm)
        logger.info { "[GENERATE-TOKEN] Successfully generated token" }
        return token
    }

    // TODO: Token blacklist / Logout invalidation
    fun verifyToken(token: String): String? =
        try {
            logger.info { "[VERIFY-TOKEN] Verifying token" }
            val subj =
                JWT
                    .require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .acceptLeeway(60.seconds.inWholeSeconds) // Accept some clock skew
                    .build()
                    .verify(token)
                    .subject
            val isAuthorized = UserRepository.authorize(subj)
            if (isAuthorized) {
                subj.also { logger.info { "[VERIFY-TOKEN] Successfully verified token" } }
            } else {
                null.also { logger.warn { "[VERIFY-TOKEN] User ${subj.maskUUID()} attempted an authorized login" } }
            }
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "[VERIFY-TOKEN] Invalid token" }
            null
        }
}
