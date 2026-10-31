package com.companyb.companyapp.identity

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUser
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.CredentialTokenRepository
import com.companyb.companyapp.identity.CredentialTokenTable
import com.companyb.companyapp.identity.CredentialTokens
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.LoginResult
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.PasswordResetDelivery
import com.companyb.companyapp.identity.PasswordResetSender
import com.companyb.companyapp.identity.RateLimiter
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.validation.PasswordPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Identity fields an invitee supplies at accept when the invite was minted without them. */
data class AcceptProfile(
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
)

object AuthService {
    private data class MintedResetCode(
        val recipient: String,
        val rawCode: String,
    )

    private val logger = KotlinLogging.logger { }
    private const val TOKEN_TABLE_NAME = "credential_token"
    private const val WEAK_PASSWORD_MESSAGE =
        "Password does not meet the policy (minimum length ${PasswordPolicy.MIN_LENGTH}, " +
            "maximum ${Password.MAX_PASSWORD_BYTES} bytes)"
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

    // reset codes live 24h, shorter than invites' 7 days: the reset surface is
    // unauthenticated self-service reachable by anyone who knows an identifier.
    private const val RESET_VALID_HOURS = 24L

    // Independent rate-limit budget per IP — login hammering must not starve resets and vice
    // versa (RateLimiter keys on the exact string).
    private const val RESET_RATE_KEY_PREFIX = "reset:"

    // DB-derived issuance skew: guarantees the minted iat lands strictly after
    // the persisted boundary read in the same login transaction.
    private const val ISSUED_AT_SKEW_SECONDS = 1L

    fun login(
        username: String,
        password: String,
        ip: String,
    ): LoginResult {
        logger.info { "[LOGIN] Login attempt from $ip" }

        // Per address, then per account: many addresses must not add up to unlimited guesses at one account.
        val refusal =
            when {
                !RateLimiter.isAllowed(ip) -> LoginResult.RateLimited
                LoginFailures.isLocked(username) -> LoginResult.AccountLocked
                else -> null
            }
        if (refusal != null) {
            logger.warn { "[LOGIN] Refused $ip: $refusal" }
            return refusal
        }

        logger.info { "[LOGIN] Verifying user login" }
        val appUser: AppUser? = UserRepository.findByUsername(username)

        val isVerified = Password.verify(password, appUser?.passwordHash)
        val userId = appUser?.let { runCatching { UUID.fromString(it.id) }.getOrNull() }
        val preliminaryOk = appUser != null && isVerified && userId != null
        if (!preliminaryOk) {
            logger.warn { "[LOGIN] Failed login attempt" }
        }

        // bind issuance to the exact credential verified: lock the account,
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
                logger.warn { "[LOGIN] Credential rotated or account inactive" }
            }
            LoginFailures.failed(username)
            LoginResult.InvalidCredentials
        } else {
            LoginFailures.succeeded(username)
            logger.info { "[LOGIN] User has logged in successfully " }
            LoginResult.Success(token, userId)
        }
    }

    /**
     * Public single-use invite redemption: consumes the code atomically (single-use +
     * expiry enforced in one UPDATE predicate, DB clock) and sets the account password. The
     * command owns its transaction (ADR-0024); the audit rows record that a pending credential
     * was set — never any secret material. changedBy is the invited user themself.
     */
    fun acceptInvite(
        rawCode: String,
        newPassword: String,
        profile: AcceptProfile = AcceptProfile(),
    ) {
        if (!PasswordPolicy.isValid(newPassword) || Password.exceedsMaxBytes(newPassword)) {
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
                // serialize with re-invite mints on the account lock before the
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
                // A code-only invite: the invitee names themselves here. Any failure rolls the
                // consume back, so the code stays live for a corrected retry.
                completePendingProfile(userId, profile)
                UserRepository.setPasswordHashInTransaction(userId, Password.create(newPassword))
                // Accepting replaces the credential: kill pre-invite tokens durably.
                UserRepository.advanceRevocationBoundaryInTransaction(userId)
                AuthAudit.passwordSet(
                    recordId = userId,
                    oldLabel = "(pending invite)",
                    newLabel = "(set by invitee)",
                )
                AuditLog.recordUpdate(
                    context = AuditContext(changedBy = userId),
                    row = AuditedRow(TOKEN_TABLE_NAME, row.id),
                    oldFields = mapOf("consumed" to "false"),
                    newFields = mapOf("consumed" to "true"),
                )
                userId
            }
        logger.info { "[INVITE-ACCEPT] Invite redeemed for ${consumedUserId.toString().maskUUID()}" }
    }

    /** A code-only invite: the invitee names themselves here, in the accept transaction. */
    private fun completePendingProfile(
        userId: UUID,
        profile: AcceptProfile,
    ) {
        if (!UserRepository.isProfilePendingInTransaction(userId)) return
        val named = AcceptProfile(profile.username.trim(), profile.email.trim(), profile.displayName.trim())
        UserService.validateProfile(named)
        UserRepository.completeProfileInTransaction(userId, named)
        UserAudit.profileCompleted(userId, named.username, named.displayName)
    }

    /**
     * public reset request. The response is uniform whether or not [identifier] matches
     * an account (true either way): enumeration resistance. A matching account gets a fresh
     * 24h PASSWORD_RESET token; any of its outstanding reset codes are invalidated first
     *  (re-invite pattern). False means the caller's IP exceeded its rate budget.
     *
     * Delivery happens after the token transaction commits. Without SMTP the code stays valid
     * for retry but is never logged (#897 fail-closed; the server-log relay needs the explicit
     * dev-only PASSWORD_RESET_DEV_RELAY opt-in). SMTP failures do not change token or rate-limit
     * semantics.
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
     * logs: production callers go through [requestPasswordReset], whose uniform true
     * response carries the enumeration resistance. Null when no account matches.
     */
    internal fun mintResetCode(identifier: String): String? = mintResetCodeForDelivery(identifier)?.rawCode

    private fun mintResetCodeForDelivery(identifier: String): MintedResetCode? =
        transaction {
            val existing =
                UserRepository.findByUsernameOrEmailInTransaction(identifier, identifier) ?: return@transaction null
            // serialize concurrent mints on the account lock: the second mint
            // observes the first mint's row as outstanding and supersedes it, so only
            // one live reset code survives.
            check(UserRepository.acquireLockInTransaction(existing.id)) { "User not found" }
            CredentialTokenRepository
                .findUnconsumedIdsInTransaction(existing.id, CredentialTokenPurpose.PASSWORD_RESET)
                .forEach { tokenId ->
                    CredentialTokenRepository.invalidateInTransaction(tokenId)
                    AuditLog.recordUpdate(
                        context = AuditContext(changedBy = existing.id),
                        row = AuditedRow(TOKEN_TABLE_NAME, tokenId),
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
                    // Reset-token validity is credential lifecycle: auth owns JVM-clock reads.
                    expiresAt =
                        OffsetDateTime.ofInstant(
                            Instant.now().plus(RESET_VALID_HOURS, ChronoUnit.HOURS),
                            ZoneOffset.UTC,
                        ),
                    createdBy = null,
                )
            AuditLog.recordInsert(
                context = AuditContext(changedBy = existing.id),
                row = AuditedRow(TOKEN_TABLE_NAME, tokenId),
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
     * authenticated logout: persists the JWT revocation boundary before returning
     * success, so pre-logout tokens stay dead across restart and backend processes.
     *
     * intentionally unaudited: routine high-volume self-revocation, exempt from the
     * every-mutation audit rule. Durability is the boundary row itself;
     * observability stays on the `[LOGOUT]` app log, avoiding one `app_user` UPDATE row
     * per logout for no security signal.
     */
    fun logout(userId: UUID) {
        transaction {
            UserRepository.advanceRevocationBoundaryInTransaction(userId)
        }
        logger.info { "[LOGOUT] Logout processed for ${userId.toString().maskUUID()}" }
    }

    /**
     * public single-use reset redemption: same atomic consume as [acceptInvite]
     * (single-use + expiry in one UPDATE predicate, DB clock), purpose-scoped to
     * PASSWORD_RESET. Replaces the password hash (the prior credential stops working) and
     * advances the persisted revocation boundary in the same transaction so live JWTs die
     * atomically with the reset. changedBy is the requesting user themself.
     */
    fun resetPassword(
        rawCode: String,
        newPassword: String,
    ) {
        if (!PasswordPolicy.isValid(newPassword) || Password.exceedsMaxBytes(newPassword)) {
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
                // same account-lock order as mint and login: redemption serializes
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
                AuditLog.recordUpdate(
                    context = AuditContext(changedBy = userId),
                    row = AuditedRow(TOKEN_TABLE_NAME, row.id),
                    oldFields = mapOf("consumed" to "false"),
                    newFields = mapOf("consumed" to "true"),
                )
                userId
            }
        logger.info { "[PASSWORD-RESET] Password reset completed for ${resetUserId.toString().maskUUID()}" }
    }

    /**
     * Self-service password change for a signed-in user: proves the current password,
     * replaces the hash and advances the revocation boundary, so every session (this one
     * included) ends and the caller signs in again with the new password.
     */
    fun changePassword(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
    ) {
        if (!PasswordPolicy.isValid(newPassword) || Password.exceedsMaxBytes(newPassword)) {
            throw ValidationException(WEAK_PASSWORD_MESSAGE)
        }
        transaction {
            check(UserRepository.acquireLockInTransaction(userId)) { "User not found" }
            val hash = MeRepository.passwordHash(userId)
            if (Password.exceedsMaxBytes(currentPassword) || !Password.verify(currentPassword, hash)) {
                throw ValidationException("Current password is wrong")
            }
            UserRepository.setPasswordHashInTransaction(userId, Password.create(newPassword))
            UserRepository.advanceRevocationBoundaryInTransaction(userId)
            AuthAudit.passwordSet(recordId = userId, oldLabel = "(previous)", newLabel = "(changed by user)")
        }
        logger.info { "[PASSWORD-CHANGE] Password changed for ${userId.toString().maskUUID()}" }
    }
}

/** Audit seam for the public auth commands: the user-row table name stays behind it. */
internal object AuthAudit {
    fun passwordSet(
        recordId: UUID,
        oldLabel: String,
        newLabel: String,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = recordId),
        row = AuditedRow(AppUserTable.tableName, recordId),
        oldFields = mapOf("password" to oldLabel),
        newFields = mapOf("password" to newLabel),
    )
}
