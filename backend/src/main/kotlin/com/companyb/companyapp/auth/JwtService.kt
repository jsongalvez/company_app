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
            // TODO: Consider RS256 for offline login
            Algorithm.HMAC256(secret)
        }

    fun generateToken(userId: String): String {
        // TODO: Mask user ID's
        logger.info { "[GENERATE-TOKEN] Generating token for $userId" }
        // TODO: Reduce token lifetime to 15 minutes (need refresh token)
        val now = Instant.now()
        val expiresAt = now.plus(1, ChronoUnit.DAYS)
        logger.info { "[GENERATE-TOKEN] Token expires at $expiresAt" }
        val token =
            JWT
                // TODO: Add and enforce:
                //  - issuer (iss)
                //  - audience (aud)
                //  - possibly token version (ver)
                .create()
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
                    // TODO: Reject tokens with missing claims (after implmenting)
                    .require(algorithm)
                    .acceptLeeway(60) // Accept some clock skew
                    .build()
                    .verify(token)
                    // TODO: Never trust without DB validation (add authenticate function)
                    .subject
            logger.info { "[VERIFY-TOKEN] Successfully verified token" }
            subj
        } catch (e: JWTVerificationException) {
            // TODO: Don't log e.message
            logger.warn { "[VERIFY-TOKEN] Invalid token: ${e.message}" }
            null
        }
}
