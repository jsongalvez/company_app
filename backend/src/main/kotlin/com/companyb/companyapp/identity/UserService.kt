package com.companyb.companyapp.identity

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.domain.CredentialTokenPurpose
import com.companyb.companyapp.dto.InviteMintRequest
import com.companyb.companyapp.dto.InviteMintResponse
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUser
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.CredentialTokenRepository
import com.companyb.companyapp.identity.CredentialTokenTable
import com.companyb.companyapp.identity.CredentialTokens
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleRepository
import com.companyb.companyapp.identity.UserCreateParams
import com.companyb.companyapp.identity.UserRepository
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.validation.EmailPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * User administration. Deactivation revokes all access immediately by persisting INACTIVE
 * status plus the JWT revocation boundary in the same transaction (#492) — already-issued
 * tokens fail the persisted-boundary authorization read without any process-local state.
 *
 * Mutating commands own exactly one transaction (#323, ADR-0024): persistence runs on it
 * via `UserRepository.*InTransaction` store operations and the audit row is inserted into
 * the same transaction, so status change + audit commit atomically or not at all.
 */
object UserService {
    private val logger = KotlinLogging.logger {}
    private const val SELF_DEACTIVATE_MESSAGE = "Cannot deactivate yourself"
    private const val SUPERUSER_ROLE = "SUPERUSER"
    private const val SUPERUSER_GUARD_MESSAGE =
        "$SUPERUSER_ROLE cannot be granted or removed through the API"
    private const val INVALID_EMAIL_MESSAGE = "Email is invalid"
    private const val INVITE_VALID_DAYS = 7L
    private const val TOKEN_TABLE_NAME = "credential_token"

    /**
     * Admin mint of a single-use invite link (#350, #346 decision 2). Creates an ACTIVE user
     * whose password hash nobody knows (random secret), applies the requested role bundle in
     * the #344 shape, and returns the raw invite code — the only copy. The invitee sets their
     * own password via [AuthService.acceptInvite].
     *
     * Re-invite recovery (#350): when username/email matches an existing account that still
     * holds an unconsumed invite, no new user is created — the outstanding link is invalidated
     * and a fresh code is minted for the existing account (with the request's role bundle
     * applied as a full replace). Without an outstanding link the duplicate conflicts 409.
     */
    fun mintInvite(
        callerId: UUID,
        request: InviteMintRequest,
    ): InviteMintResponse {
        if (!EmailPolicy.isValid(request.email)) {
            throw ValidationException(INVALID_EMAIL_MESSAGE)
        }
        if (request.roles.contains(SUPERUSER_ROLE)) {
            throw ValidationException(SUPERUSER_GUARD_MESSAGE)
        }
        // Invite validity is credential lifecycle (#322): the auth cluster owns JVM-clock reads.
        val expiresAt =
            OffsetDateTime.ofInstant(
                Instant.now().plus(INVITE_VALID_DAYS, ChronoUnit.DAYS),
                ZoneOffset.UTC,
            )
        val minted: MintedInvite = transaction { mintInviteInTransaction(callerId, request, expiresAt) }
        logger.info {
            val action = if (minted.createdNew) "Created user" else "Re-invited existing user"
            "[INVITE-MINT] $action ${minted.userId.toString().maskUUID()}"
        }
        return InviteMintResponse(
            userId = minted.userId.toString(),
            inviteCode = minted.rawCode,
            expiresAt = expiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    /** The mint command's transaction body (ADR-0024): one transaction, audit rows inside. */
    private fun mintInviteInTransaction(
        callerId: UUID,
        request: InviteMintRequest,
        expiresAt: OffsetDateTime,
    ): MintedInvite {
        val existing = UserRepository.findByUsernameOrEmailInTransaction(request.username, request.email)
        // #505 — serialize with acceptInvite on the account lock before observing or
        // invalidating outstanding codes; the check-then-invalidate runs under the lock.
        if (existing != null) {
            val locked = UserRepository.acquireLockInTransaction(existing.id)
            check(locked) { "User row not found after invite target resolution" }
        }
        val reinvite =
            existing != null &&
                CredentialTokenRepository.hasOutstandingInTransaction(
                    existing.id,
                    CredentialTokenPurpose.INVITE,
                )
        var createdNew = false
        val targetId: UUID
        if (reinvite) {
            targetId = invalidateOutstandingInvites(callerId, existing.id)
        } else {
            createdNew = true
            // A hash of a random secret: the account cannot be logged into until the
            // invitee accepts and sets their own password.
            targetId =
                UserRepository.createUserInTransaction(
                    UserCreateParams(
                        username = request.username,
                        passwordHash = Password.create(CredentialTokens.generate()),
                        email = request.email,
                        displayName = request.displayName,
                    ),
                )
        }
        check(UserRepository.acquireLockInTransaction(targetId)) { "User row not found after invite target resolution" }
        val beforeRoles =
            if (createdNew) {
                emptyList()
            } else {
                RoleRepository.findRoleNamesForUserInTransaction(targetId)
            }
        replaceMembershipsInTransaction(callerId, targetId, request.roles, beforeRoles)
        if (createdNew) {
            UserAudit.userInserted(
                recordId = targetId,
                changedBy = callerId,
                username = request.username,
                displayName = request.displayName,
            )
        }
        val rawCode = insertInviteToken(targetId, callerId, expiresAt)
        return MintedInvite(targetId, rawCode, createdNew)
    }

    /** Re-invite leg (#350): kill every outstanding code, one audit row each; returns the account id. */
    private fun invalidateOutstandingInvites(
        callerId: UUID,
        targetId: UUID,
    ): UUID {
        CredentialTokenRepository
            .findUnconsumedIdsInTransaction(targetId, CredentialTokenPurpose.INVITE)
            .forEach { tokenId ->
                CredentialTokenRepository.invalidateInTransaction(tokenId)
                AuditLog.recordUpdate(
                    tableName = TOKEN_TABLE_NAME,
                    recordId = tokenId,
                    changedBy = callerId,
                    oldFields = mapOf("consumed" to "false"),
                    newFields = mapOf("consumed" to "true"),
                )
            }
        return targetId
    }

    /** Mints the single-use token and writes its audit row; the raw code lives only in the response. */
    private fun insertInviteToken(
        targetId: UUID,
        callerId: UUID,
        expiresAt: OffsetDateTime,
    ): String {
        val rawCode = CredentialTokens.generate()
        val tokenId =
            CredentialTokenRepository.insertInTransaction(
                tokenHash = CredentialTokens.hash(rawCode),
                purpose = CredentialTokenPurpose.INVITE,
                userId = targetId,
                expiresAt = expiresAt,
                createdBy = callerId,
            )
        AuditLog.recordInsert(
            tableName = TOKEN_TABLE_NAME,
            recordId = tokenId,
            changedBy = callerId,
            fields =
                mapOf(
                    "userId" to targetId.toString(),
                    "purpose" to CredentialTokenPurpose.INVITE.name,
                    "expiresAt" to expiresAt.toString(),
                ),
        )
        return rawCode
    }

    private data class MintedInvite(
        val userId: UUID,
        val rawCode: String,
        val createdNew: Boolean,
    )

    fun getRoles(): List<RoleResponse> =
        RoleRepository
            .findAllWithCapabilities()
            .filter { it.name != SUPERUSER_ROLE }
            .map { RoleResponse(name = it.name, capabilities = it.capabilities) }

    /**
     * Full replace of the target's role memberships (#344). SUPERUSER is guarded in both
     * directions (grant and removal) pending the #346 onboarding policy decisions.
     */
    fun replaceRoles(
        callerId: UUID,
        targetUserId: UUID,
        roleNames: List<String>,
    ) {
        if (roleNames.contains(SUPERUSER_ROLE)) {
            throw ValidationException(SUPERUSER_GUARD_MESSAGE)
        }
        transaction {
            if (!UserRepository.acquireLockInTransaction(targetUserId)) {
                throw NotFoundException("User not found")
            }
            val before = RoleRepository.findRoleNamesForUserInTransaction(targetUserId)
            if (before.contains(SUPERUSER_ROLE)) {
                throw ValidationException(SUPERUSER_GUARD_MESSAGE)
            }
            replaceMembershipsInTransaction(callerId, targetUserId, roleNames, before)
        }
        logger.info { "[USER-ROLES-REPLACE] Role replace processed for ${targetUserId.toString().maskUUID()}" }
    }

    /** Transaction-local write half of [replaceRoles]: resolve, rewrite, audit on change. */
    private fun replaceMembershipsInTransaction(
        callerId: UUID,
        targetUserId: UUID,
        roleNames: List<String>,
        before: List<String>,
    ) {
        val unknownNames = roleNames.filter { RoleRepository.findIdByNameInTransaction(it) == null }.distinct()
        if (unknownNames.isNotEmpty()) {
            throw ValidationException("Unknown role(s): ${unknownNames.joinToString(", ")}")
        }
        val requestedSet = roleNames.toSortedSet()
        val roleIds =
            requestedSet.mapNotNull { roleName -> RoleRepository.findIdByNameInTransaction(roleName) }
        RoleRepository.replaceUserRolesInTransaction(targetUserId, roleIds)
        val after = RoleRepository.findRoleNamesForUserInTransaction(targetUserId)
        if (after != before) {
            UserAudit.rolesReplaced(
                recordId = targetUserId,
                changedBy = callerId,
                before = before,
                after = after,
            )
        }
    }

    fun deactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        if (callerId == targetUserId) {
            throw ValidationException(SELF_DEACTIVATE_MESSAGE)
        }
        transaction {
            val t =
                UserRepository.deactivateInTransaction(targetUserId)
                    ?: throw NotFoundException("User not found")
            if (t.changed) {
                UserAudit.statusUpdated(AuditContext(callerId), t.before, t.after)
            }
        }
        logger.info { "[DEACTIVATE] Deactivation request processed" }
    }

    fun reactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        transaction {
            val t =
                UserRepository.reactivateInTransaction(targetUserId)
                    ?: throw NotFoundException("User not found")
            if (t.changed) {
                UserAudit.statusUpdated(AuditContext(callerId), t.before, t.after)
            }
        }
        logger.info { "[REACTIVATE] Reactivation request processed" }
    }

    fun listUsers(): List<UserSummaryResponse> {
        val users = UserRepository.findAll()
        val assignments =
            UserRepository
                .findActiveAssignmentsWithBranch()
                .groupBy { it.userId }
        val rolesByUser = RoleRepository.findRoleNamesByUser(users.map { UUID.fromString(it.id) })
        return users.map { user ->
            UserSummaryResponse(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                status =
                    com.companyb.companyapp.domain.UserStatus
                        .valueOf(user.status.name),
                deactivatedAt =
                    user.deactivatedAt
                        ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                assignments =
                    assignments[UUID.fromString(user.id)]
                        .orEmpty()
                        .map { assignment ->
                            UserAssignmentResponse(
                                assignmentId = assignment.assignmentId.toString(),
                                branchId = assignment.branchId.toString(),
                                branchName = assignment.branchName,
                                slot = assignment.slot,
                            )
                        },
                roles = rolesByUser[UUID.fromString(user.id)].orEmpty(),
            )
        }
    }
}

/**
 * User audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not. Global-scoped — user administration has no
 * branch/day context.
 */
internal object UserAudit {
    fun statusUpdated(
        context: AuditContext,
        before: AppUser,
        after: AppUser,
    ) = AuditLog.recordUpdate(
        tableName = AppUserTable.tableName,
        recordId = UUID.fromString(after.id),
        before = before,
        after = after,
        changedBy = context.changedBy,
        auditFields = AppUserTable::auditFields,
    )

    fun userInserted(
        recordId: UUID,
        changedBy: UUID,
        username: String,
        displayName: String,
    ) = AuditLog.recordInsert(
        tableName = AppUserTable.tableName,
        recordId = recordId,
        changedBy = changedBy,
        fields =
            mapOf(
                "id" to recordId.toString(),
                "username" to username,
                "status" to com.companyb.companyapp.domain.UserStatus.ACTIVE.name,
                "displayName" to displayName,
            ),
    )

    fun rolesReplaced(
        recordId: UUID,
        changedBy: UUID,
        before: List<String>,
        after: List<String>,
    ) = AuditLog.recordUpdate(
        tableName = UserRoleTable.tableName,
        recordId = recordId,
        oldFields = mapOf("roles" to before.joinToString(",")),
        newFields = mapOf("roles" to after.joinToString(",")),
        changedBy = changedBy,
    )
}
