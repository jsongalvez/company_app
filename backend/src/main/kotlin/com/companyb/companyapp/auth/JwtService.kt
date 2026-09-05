package com.companyb.companyapp.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object JwtService {
    private data class Runtime(
        val issuer: String,
        val audience: String,
        val algorithm: Algorithm,
        val verifier: com.auth0.jwt.JWTVerifier,
    )

    private val logger = KotlinLogging.logger {}
    private const val MIN_SECRET_LENGTH = 32

    @Volatile
    private var runtime: Runtime? = null

    fun init(config: AppConfig) {
        val secret = config.jwtSecret
        require(secret.length >= MIN_SECRET_LENGTH) {
            "JWT_SECRET must be at least $MIN_SECRET_LENGTH characters"
        }

        val issuer = config.jwtIssuer
        val audience = config.jwtAudience
        val algo = Algorithm.HMAC256(secret)
        val verifier =
            JWT
                .require(algo)
                .withIssuer(issuer)
                .withAudience(audience)
                .acceptLeeway(60.seconds.inWholeSeconds)
                .build()
        runtime = Runtime(issuer, audience, algo, verifier)
    }

    fun generateToken(userId: String): String {
        logger.info { "[GENERATE-TOKEN] Generating token for ${userId.maskUUID()}" }
        val configured = runtime ?: error("JwtService.init() must be called before generateToken()")
        val now = Instant.now()
        val expiresAt = now.plus(1, ChronoUnit.DAYS)
        logger.info { "[GENERATE-TOKEN] Token expires at $expiresAt" }
        val token =
            JWT
                .create()
                .withIssuer(configured.issuer)
                .withAudience(configured.audience)
                .withSubject(userId)
                .withExpiresAt(Date.from(expiresAt))
                .withIssuedAt(Date.from(now))
                .sign(configured.algorithm)
        logger.info { "[GENERATE-TOKEN] Successfully generated token" }
        return token
    }

    @Suppress("ReturnCount", "UnreachableCode")
    fun verifyToken(token: String): String? =
        try {
            val configured = runtime ?: error("JwtService.init() must be called before verifyToken()")
            val decoded = configured.verifier.verify(token)
            val subj =
                decoded.subject ?: return null.also {
                    logger.warn { "[VERIFY-TOKEN] Token has no subject" }
                }
            val parsedId =
                runCatching { UUID.fromString(subj) }
                    .getOrElse {
                        logger.warn { "[VERIFY-TOKEN] Invalid UUID in subject: ${subj.maskUUID()}" }
                        return null
                    }
            // Missing iat is rejected explicitly (#492); tokens we sign always carry iat.
            val issuedAt =
                decoded.issuedAt?.toInstant() ?: return null.also {
                    logger.warn { "[VERIFY-TOKEN] Token has no issued-at claim" }
                }
            if (UserRepository.authorize(parsedId, issuedAt)) {
                subj
            } else {
                null.also { logger.warn { "[VERIFY-TOKEN] User ${subj.maskUUID()} is unauthorized" } }
            }
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "[VERIFY-TOKEN] Invalid token" }
            null
        }
}
