package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.dto.RoleResponse
import com.companyb.companyapp.dto.UserAssignmentResponse
import com.companyb.companyapp.dto.UserCreateRequest
import com.companyb.companyapp.dto.UserSummaryResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.RoleRepository
import com.companyb.companyapp.repository.UserCreateParams
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.validation.EmailPolicy
import com.companyb.companyapp.validation.PasswordPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * User administration. Deactivation revokes all access immediately by both persisting
 * INACTIVE status (so future tokens fail DB authorization) and adding the user to the
 * in-memory [DenyList] (so already-issued tokens are rejected before any DB lookup).
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
    private const val WEAK_PASSWORD_MESSAGE =
        "Password does not meet the policy (minimum length ${PasswordPolicy.MIN_LENGTH})"
    private const val INVALID_EMAIL_MESSAGE = "Email is invalid"

    /**
     * Admin user creation (#344). Creates an ACTIVE user with no roles, no branch
     * assignments — zero effective capabilities until MANAGE_USERS assigns a role.
     */
    fun create(
        callerId: UUID,
        request: UserCreateRequest,
    ): UserSummaryResponse {
        if (!PasswordPolicy.isValid(request.password)) {
            throw ValidationException(WEAK_PASSWORD_MESSAGE)
        }
        if (!EmailPolicy.isValid(request.email)) {
            throw ValidationException(INVALID_EMAIL_MESSAGE)
        }
        val passwordHash = Password.create(request.password)
        val createdId: UUID =
            transaction {
                val id: UUID =
                    UserRepository.createUserInTransaction(
                        UserCreateParams(
                            username = request.username,
                            passwordHash = passwordHash,
                            email = request.email,
                            displayName = request.displayName,
                        ),
                    )
                UserAudit.userInserted(
                    recordId = id,
                    changedBy = callerId,
                    username = request.username,
                    displayName = request.displayName,
                )
                id
            }
        logger.info { "[USER-CREATE] Admin created user ${createdId.toString().maskUUID()}" }
        return UserSummaryResponse(
            id = createdId.toString(),
            username = request.username,
            displayName = request.displayName,
            status = com.companyb.companyapp.domain.UserStatus.ACTIVE,
            deactivatedAt = null,
            assignments = emptyList(),
            roles = emptyList(),
        )
    }

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
            if (!UserRepository.existsById(targetUserId)) {
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
        val transition =
            transaction {
                val t =
                    UserRepository.deactivateInTransaction(targetUserId)
                        ?: throw NotFoundException("User not found")
                if (t.changed) {
                    UserAudit.statusUpdated(AuditContext(callerId), t.before, t.after)
                }
                t
            }
        DenyList.denyAt(
            targetUserId,
            transition.after.jwtRevokedAt?.toInstant()
                ?: error("Deactivation did not persist JWT revocation"),
        )
        logger.info { "[DEACTIVATE] Deactivation request processed; user added to deny list" }
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
    ) = AuditLogRepository.recordUpdate(
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
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
        tableName = UserRoleTable.tableName,
        recordId = recordId,
        oldFields = mapOf("roles" to before.joinToString(",")),
        newFields = mapOf("roles" to after.joinToString(",")),
        changedBy = changedBy,
    )
}
