package com.companyb.companyapp.identity

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.logging.maskUUID
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

    /** Credential-generation claim: binds the token to the password version verified at login. */
    const val CREDENTIAL_VERSION_CLAIM = "cred_ver"

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

    /** Marks a replay token: it only buys a fresh sign-in token (`/auth/refresh`), never an API call. */
    const val USE_CLAIM = "use"
    private const val REPLAY_USE = "replay"

    /** A phone may be off signal for a whole day or more; its queued saves still go out within this. */
    const val REPLAY_DAYS = 7L

    fun generateToken(userId: String): String = generateToken(userId, 0L, Instant.now())

    /** The long token a phone keeps to sign itself in again after days without signal. */
    fun generateReplayToken(
        userId: String,
        credentialVersion: Long,
        issuedAt: Instant,
    ): String = generateToken(userId, credentialVersion, issuedAt, replay = true)

    /**
     * Version-bound issuance: [credentialVersion] is the account's current
     * generation, [issuedAt] the DB-derived instant guaranteed strictly after the
     * persisted revocation boundary so immediate fresh logins verify without sleeps.
     */
    fun generateToken(
        userId: String,
        credentialVersion: Long,
        issuedAt: Instant,
        replay: Boolean = false,
    ): String {
        logger.info { "[GENERATE-TOKEN] Generating token for ${userId.maskUUID()}" }
        val configured = runtime ?: error("JwtService.init() must be called before generateToken()")
        val expiresAt = issuedAt.plus(if (replay) REPLAY_DAYS else 1, ChronoUnit.DAYS)
        logger.info { "[GENERATE-TOKEN] Token expires at $expiresAt" }
        val token =
            JWT
                .create()
                .withIssuer(configured.issuer)
                .withAudience(configured.audience)
                .withSubject(userId)
                .withExpiresAt(Date.from(expiresAt))
                .withIssuedAt(Date.from(issuedAt))
                .withClaim(CREDENTIAL_VERSION_CLAIM, credentialVersion)
                .apply { if (replay) withClaim(USE_CLAIM, REPLAY_USE) }
                .sign(configured.algorithm)
        logger.info { "[GENERATE-TOKEN] Successfully generated token" }
        return token
    }

    /** The user a replay token belongs to and its credential version, or null. */
    fun verifyReplayToken(token: String): Pair<String, Long>? =
        verifyToken(token, replay = true)?.let { subject ->
            subject to (credentialVersionOf(token) ?: return null)
        }

    private fun credentialVersionOf(token: String): Long? =
        runCatching {
            val claim = JWT.decode(token).getClaim(CREDENTIAL_VERSION_CLAIM)
            claim.takeIf { !it.isMissing && !it.isNull }?.asLong() ?: 0L
        }.getOrNull()

    /** The user an API token belongs to, or null. A replay token is refused here. */
    fun verifyToken(
        token: String,
        replay: Boolean = false,
    ): String? =
        try {
            val configured = runtime ?: error("JwtService.init() must be called before verifyToken()")
            val decoded = configured.verifier.verify(token)
            val isReplay = decoded.getClaim(USE_CLAIM).asString() == REPLAY_USE
            if (isReplay != replay) {
                logger.warn { "[VERIFY-TOKEN] Token used for the wrong purpose" }
                return null
            }
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
            // Missing iat is rejected explicitly; tokens we sign always carry iat.
            val issuedAt =
                decoded.issuedAt?.toInstant() ?: return null.also {
                    logger.warn { "[VERIFY-TOKEN] Token has no issued-at claim" }
                }
            // Older tokens carry no version claim — treat as generation 0.
            val versionClaim = decoded.getClaim(CREDENTIAL_VERSION_CLAIM)
            val credentialVersion =
                versionClaim.takeIf { !it.isMissing && !it.isNull }?.asLong() ?: 0L
            if (UserRepository.authorize(parsedId, issuedAt, credentialVersion)) {
                subj
            } else {
                null.also { logger.warn { "[VERIFY-TOKEN] User ${subj.maskUUID()} is unauthorized" } }
            }
        } catch (e: JWTVerificationException) {
            logger.warn(e) { "[VERIFY-TOKEN] Invalid token" }
            null
        }
}
