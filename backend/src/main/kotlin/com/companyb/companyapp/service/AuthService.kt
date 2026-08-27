package com.companyb.companyapp.service

import com.companyb.companyapp.auth.CredentialTokens
import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.auth.PasswordResetDelivery
import com.companyb.companyapp.auth.PasswordResetSender
import com.companyb.companyapp.auth.RateLimiter
import com.companyb.companyapp.domain.CredentialTokenPurpose
import com.companyb.companyapp.domain.LoginResult
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CredentialTokenRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.CredentialTokenTable
import com.companyb.companyapp.validation.PasswordPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

object AuthService {
    private data class MintedResetCode(
        val recipient: String,
        val rawCode: String,
    )

    private val logger = KotlinLogging.logger { }
    private const val TOKEN_TABLE_NAME = "credential_token"
    private const val WEAK_PASSWORD_MESSAGE =
        "Password does not meet the policy (minimum length ${PasswordPolicy.MIN_LENGTH})"
    private const val INVITE_INVALID_MESSAGE = "This invite code is invalid"
    private const val INVITE_USED_MESSAGE =
        "This invite code has already been used. Ask an administrator for a new one."
    private const val INVITE_EXPIRED_MESSAGE =
        "This invite code has expired. Ask an administrator for a new one."
    private const val RESET_INVALID_MESSAGE = "This reset code is invalid"
    private const val RESET_USED_MESSAGE =
        "This reset code has already been used. Request a new one."
    private const val RESET_EXPIRED_MESSAGE =
        "This reset code has expired. Request a new one."

    // #353 — reset codes live 24h, shorter than invites' 7 days: the reset surface is
    // unauthenticated self-service reachable by anyone who knows an identifier.
    private const val RESET_VALID_HOURS = 24L

    // Independent rate-limit budget per IP — login hammering must not starve resets and vice
    // versa (RateLimiter keys on the exact string).
    private const val RESET_RATE_KEY_PREFIX = "reset:"

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

    /**
     * Public single-use invite redemption (#350): consumes the code atomically (single-use +
     * expiry enforced in one UPDATE predicate, DB clock) and sets the account password. The
     * command owns its transaction (ADR-0024); the audit rows record that a pending credential
     * was set — never any secret material. changedBy is the invited user themself.
     */
    fun acceptInvite(
        rawCode: String,
        newPassword: String,
    ) {
        if (!PasswordPolicy.isValid(newPassword)) {
            throw ValidationException(WEAK_PASSWORD_MESSAGE)
        }
        val consumedUserId: UUID =
            transaction {
                val row =
                    CredentialTokenRepository.findByHashAndPurposeInTransaction(
                        CredentialTokens.hash(rawCode),
                        CredentialTokenPurpose.INVITE,
                    ) ?: throw ValidationException(INVITE_INVALID_MESSAGE)
                if (row.consumedAt != null) {
                    throw ValidationException(INVITE_USED_MESSAGE)
                }
                val userId = row.userId
                if (!CredentialTokenRepository.consumeIfLiveInTransaction(row.id)) {
                    // Lost the single-use race or expired between read and write — reclassify
                    // precisely so the message names the real failure.
                    val reread =
                        CredentialTokenRepository.findByIdInTransaction(row.id)
                            ?: throw ValidationException(INVITE_INVALID_MESSAGE)
                    throw ValidationException(
                        if (reread.consumedAt != null) INVITE_USED_MESSAGE else INVITE_EXPIRED_MESSAGE,
                    )
                }
                UserRepository.setPasswordHashInTransaction(userId, Password.create(newPassword))
                AuthAudit.passwordSet(
                    recordId = userId,
                    oldLabel = "(pending invite)",
                    newLabel = "(set by invitee)",
                )
                AuditLogRepository.recordUpdate(
                    tableName = TOKEN_TABLE_NAME,
                    recordId = row.id,
                    changedBy = userId,
                    oldFields = mapOf("consumed" to "false"),
                    newFields = mapOf("consumed" to "true"),
                )
                userId
            }
        logger.info { "[INVITE-ACCEPT] Invite redeemed for ${consumedUserId.toString().maskUUID()}" }
    }

    /**
     * #353 — public reset request. The response is uniform whether or not [identifier] matches
     * an account (true either way): enumeration resistance. A matching account gets a fresh
     * 24h PASSWORD_RESET token; any of its outstanding reset codes are invalidated first
     * (#350 re-invite pattern). False means the caller's IP exceeded its rate budget.
     *
     * Delivery happens after the token transaction commits. Missing configuration uses the
     * existing operator log relay; SMTP failures do not change token or rate-limit semantics.
     */
    fun requestPasswordReset(
        identifier: String,
        ip: String,
    ): Boolean = requestPasswordReset(identifier, ip, senderOverride = null)

    /** Test seam for exercising delivery failures without making network calls. */
    internal fun requestPasswordReset(
        identifier: String,
        ip: String,
        senderOverride: PasswordResetSender?,
    ): Boolean {
        if (!RateLimiter.isAllowed(RESET_RATE_KEY_PREFIX + ip)) {
            logger.warn { "[PASSWORD-RESET] Rate limiting reset requests from $ip" }
            return false
        }
        val trimmed = identifier.trim()
        if (trimmed.isEmpty()) {
            return true
        }
        val minted = mintResetCodeForDelivery(trimmed)
        if (minted != null) {
            PasswordResetDelivery.deliver(
                identifier = trimmed,
                recipient = minted.recipient,
                rawCode = minted.rawCode,
                validityHours = RESET_VALID_HOURS,
                senderOverride = senderOverride,
            )
        }
        return true
    }

    /**
     * The request leg's write half, split so tests can obtain the raw code without parsing
     * logs (#353): production callers go through [requestPasswordReset], whose uniform true
     * response carries the enumeration resistance. Null when no account matches.
     */
    internal fun mintResetCode(identifier: String): String? = mintResetCodeForDelivery(identifier)?.rawCode

    private fun mintResetCodeForDelivery(identifier: String): MintedResetCode? =
        transaction {
            val existing =
                UserRepository.findByUsernameOrEmailInTransaction(identifier, identifier) ?: return@transaction null
            CredentialTokenRepository
                .findUnconsumedIdsInTransaction(existing.id, CredentialTokenPurpose.PASSWORD_RESET)
                .forEach { tokenId ->
                    CredentialTokenRepository.invalidateInTransaction(tokenId)
                    AuditLogRepository.recordUpdate(
                        tableName = TOKEN_TABLE_NAME,
                        recordId = tokenId,
                        changedBy = existing.id,
                        oldFields = mapOf("consumed" to "false"),
                        newFields = mapOf("consumed" to "true"),
                    )
                }
            val rawCode = CredentialTokens.generate()
            val tokenId =
                CredentialTokenRepository.insertInTransaction(
                    tokenHash = CredentialTokens.hash(rawCode),
                    purpose = CredentialTokenPurpose.PASSWORD_RESET,
                    userId = existing.id,
                    // Reset-token validity is credential lifecycle (#322): auth owns JVM-clock reads.
                    expiresAt =
                        OffsetDateTime.ofInstant(
                            Instant.now().plus(RESET_VALID_HOURS, ChronoUnit.HOURS),
                            ZoneOffset.UTC,
                        ),
                    createdBy = null,
                )
            AuditLogRepository.recordInsert(
                tableName = TOKEN_TABLE_NAME,
                recordId = tokenId,
                changedBy = existing.id,
                fields =
                    mapOf(
                        "purpose" to CredentialTokenPurpose.PASSWORD_RESET.name,
                        "createdBy" to "(self-requested)",
                    ),
            )
            MintedResetCode(
                recipient = existing.email,
                rawCode = rawCode,
            )
        }

    /**
     * #353 — public single-use reset redemption: same atomic consume as [acceptInvite]
     * (single-use + expiry in one UPDATE predicate, DB clock), purpose-scoped to
     * PASSWORD_RESET. Replaces the password hash (the prior credential stops working) and
     * denies the user's live JWTs so an attacker holding a session cannot outlive the reset.
     * changedBy is the requesting user themself.
     */
    fun resetPassword(
        rawCode: String,
        newPassword: String,
    ) {
        if (!PasswordPolicy.isValid(newPassword)) {
            throw ValidationException(WEAK_PASSWORD_MESSAGE)
        }
        val resetUserId: UUID =
            transaction {
                val row =
                    CredentialTokenRepository.findByHashAndPurposeInTransaction(
                        CredentialTokens.hash(rawCode),
                        CredentialTokenPurpose.PASSWORD_RESET,
                    ) ?: throw ValidationException(RESET_INVALID_MESSAGE)
                if (row.consumedAt != null) {
                    throw ValidationException(RESET_USED_MESSAGE)
                }
                val userId = row.userId
                if (!CredentialTokenRepository.consumeIfLiveInTransaction(row.id)) {
                    val reread =
                        CredentialTokenRepository.findByIdInTransaction(row.id)
                            ?: throw ValidationException(RESET_INVALID_MESSAGE)
                    throw ValidationException(
                        if (reread.consumedAt != null) RESET_USED_MESSAGE else RESET_EXPIRED_MESSAGE,
                    )
                }
                UserRepository.setPasswordHashInTransaction(userId, Password.create(newPassword))
                AuthAudit.passwordSet(
                    recordId = userId,
                    oldLabel = "(previous)",
                    newLabel = "(set via password reset)",
                )
                AuditLogRepository.recordUpdate(
                    tableName = TOKEN_TABLE_NAME,
                    recordId = row.id,
                    changedBy = userId,
                    oldFields = mapOf("consumed" to "false"),
                    newFields = mapOf("consumed" to "true"),
                )
                userId
            }
        DenyList.deny(resetUserId)
        logger.info { "[PASSWORD-RESET] Password reset completed for ${resetUserId.toString().maskUUID()}" }
    }
}

/** Audit seam for the public auth commands (#324): the user-row table name stays behind it. */
internal object AuthAudit {
    fun passwordSet(
        recordId: UUID,
        oldLabel: String,
        newLabel: String,
    ) = AuditLogRepository.recordUpdate(
        tableName = AppUserTable.tableName,
        recordId = recordId,
        changedBy = recordId,
        oldFields = mapOf("password" to oldLabel),
        newFields = mapOf("password" to newLabel),
    )
}
