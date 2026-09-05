package com.companyb.companyapp.service

import com.companyb.companyapp.auth.CredentialTokens
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

    // #505 — DB-derived issuance skew: guarantees the minted iat lands strictly after
    // the persisted boundary read in the same login transaction.
    private const val ISSUED_AT_SKEW_SECONDS = 1L

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
        val userId = appUser?.let { runCatching { UUID.fromString(it.id) }.getOrNull() }
        val preliminaryOk = appUser != null && isVerified && userId != null
        if (!preliminaryOk) {
            logger.warn { "[LOGIN] Failed login attempt for user: $username" }
        }

        // #505 — bind issuance to the exact credential verified: lock the account,
        // prove the hash is still current and ACTIVE, then mint with a DB-derived
        // instant strictly after the persisted boundary so fresh logins verify
        // immediately without sleeps. A concurrent reset/invite-accept rotates the
        // hash or deactivates first, and this login fails closed.
        val token: String? =
            if (!preliminaryOk) {
                null
            } else {
                transaction {
                    val liveVersion =
                        UserRepository.revalidateCredentialLockedInTransaction(userId, appUser.passwordHash)
                            ?: return@transaction null
                    val boundary = UserRepository.revocationBoundaryInTransaction(userId)
                    val issuedAt =
                        UserRepository.dbNowInTransaction().toInstant().plusSeconds(ISSUED_AT_SKEW_SECONDS)
                    // Belt-and-braces: the DB-derived instant is already after the boundary,
                    // but never mint a token the authorization read would reject.
                    if (boundary != null && !issuedAt.isAfter(boundary.toInstant())) {
                        return@transaction null
                    }
                    JwtService.generateToken(userId.toString(), liveVersion, issuedAt)
                }
            }
        return if (!preliminaryOk || token == null) {
            if (preliminaryOk) {
                logger.warn { "[LOGIN] Credential rotated or account inactive for user: $username" }
            }
            LoginResult.InvalidCredentials
        } else {
            logger.info { "[LOGIN] User has logged in successfully " }
            LoginResult.Success(token)
        }
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
                // #505 — serialize with re-invite mints on the account lock before the
                // single-use consume, so a concurrent mint cannot leave a second live code.
                check(UserRepository.acquireLockInTransaction(userId)) { "User not found" }
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
                // Accepting replaces the credential: kill pre-invite tokens durably (#505).
                UserRepository.advanceRevocationBoundaryInTransaction(userId)
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
            // #505 — serialize concurrent mints on the account lock: the second mint
            // observes the first mint's row as outstanding and supersedes it, so only
            // one live reset code survives.
            check(UserRepository.acquireLockInTransaction(existing.id)) { "User not found" }
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
     * #492 — authenticated logout: persists the JWT revocation boundary before returning
     * success, so pre-logout tokens stay dead across restart and backend processes.
     */
    fun logout(userId: UUID) {
        transaction {
            UserRepository.advanceRevocationBoundaryInTransaction(userId)
        }
        logger.info { "[LOGOUT] Logout processed for ${userId.toString().maskUUID()}" }
    }

    /**
     * #353 — public single-use reset redemption: same atomic consume as [acceptInvite]
     * (single-use + expiry in one UPDATE predicate, DB clock), purpose-scoped to
     * PASSWORD_RESET. Replaces the password hash (the prior credential stops working) and
     * advances the persisted revocation boundary in the same transaction so live JWTs die
     * atomically with the reset. changedBy is the requesting user themself.
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
                // #505 — same account-lock order as mint and login: redemption serializes
                // with concurrent mints and logins on the user row.
                check(UserRepository.acquireLockInTransaction(userId)) { "User not found" }
                if (!CredentialTokenRepository.consumeIfLiveInTransaction(row.id)) {
                    val reread =
                        CredentialTokenRepository.findByIdInTransaction(row.id)
                            ?: throw ValidationException(RESET_INVALID_MESSAGE)
                    throw ValidationException(
                        if (reread.consumedAt != null) RESET_USED_MESSAGE else RESET_EXPIRED_MESSAGE,
                    )
                }
                UserRepository.setPasswordHashInTransaction(userId, Password.create(newPassword))
                UserRepository.advanceRevocationBoundaryInTransaction(userId)
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
