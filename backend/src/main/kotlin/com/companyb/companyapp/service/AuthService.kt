package com.companyb.companyapp.service

import com.companyb.companyapp.auth.CredentialTokens
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
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
import java.util.UUID

object AuthService {
    private val logger = KotlinLogging.logger { }
    private const val TOKEN_TABLE_NAME = "credential_token"
    private const val WEAK_PASSWORD_MESSAGE =
        "Password does not meet the policy (minimum length ${PasswordPolicy.MIN_LENGTH})"
    private const val INVITE_INVALID_MESSAGE = "This invite code is invalid"
    private const val INVITE_USED_MESSAGE =
        "This invite code has already been used. Ask an administrator for a new one."
    private const val INVITE_EXPIRED_MESSAGE =
        "This invite code has expired. Ask an administrator for a new one."

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
                AuditLogRepository.recordUpdate(
                    tableName = AppUserTable.tableName,
                    recordId = userId,
                    changedBy = userId,
                    oldFields = mapOf("password" to "(pending invite)"),
                    newFields = mapOf("password" to "(set by invitee)"),
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
}
