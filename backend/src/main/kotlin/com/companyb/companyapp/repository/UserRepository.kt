package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.RegistrationConflictException
import com.companyb.companyapp.exception.RegistrationConflictField
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger { }

/** Active assignment projected with its branch name — the user-list shape. */
data class UserBranchAssignmentSummary(
    val userId: UUID,
    val branchId: UUID,
    val branchName: String,
    val slot: Short,
)

data class UserCreateParams(
    val username: String,
    val passwordHash: String,
    val email: String,
    val displayName: String,
)

object UserRepository {
    fun findByUsername(username: String): AppUser? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.username eq username }
                .map { row ->
                    AppUser(
                        id = row[AppUserTable.id].toString(),
                        username = row[AppUserTable.username],
                        passwordHash = row[AppUserTable.passwordHash],
                    )
                }.singleOrNull()
        }.also { logger.info { "[FIND-BY-USERNAME] Fetched username" } }

    fun isEmailTaken(email: String): Boolean =
        transaction {
            AppUserTable
                .select(AppUserTable.id)
                .where { AppUserTable.email eq email }
                .empty()
                .not()
        }.also {
            if (it) {
                logger.info { "[IS-EMAIL-TAKEN] Email is taken" }
            }
        }

    fun createUser(
        params: UserCreateParams,
        auditFn: (UUID) -> Unit = {},
    ): UUID =
        transaction {
            val insert =
                AppUserTable.insertIgnore {
                    it[AppUserTable.username] = params.username
                    it[AppUserTable.passwordHash] = params.passwordHash
                    it[AppUserTable.email] = params.email
                    it[AppUserTable.displayName] = params.displayName
                    it[AppUserTable.status] = UserStatus.ACTIVE
                    it[AppUserTable.createdAt] = CurrentTimestampWithTimeZone
                }
            if (insert.insertedCount == 0) {
                val conflict =
                    when {
                        AppUserTable
                            .select(AppUserTable.id)
                            .where { AppUserTable.username eq params.username }
                            .empty()
                            .not() -> {
                            RegistrationConflictField.USERNAME
                        }

                        AppUserTable
                            .select(AppUserTable.id)
                            .where { AppUserTable.email eq params.email }
                            .empty()
                            .not() -> {
                            RegistrationConflictField.EMAIL
                        }

                        else -> {
                            error("Registration insert was ignored without a username or email conflict")
                        }
                    }
                throw RegistrationConflictException(conflict)
            }
            val id = insert[AppUserTable.id]
            auditFn(id)
            id
        }.also { logger.info { "[CREATE-USER] Added user to ${AppUserTable.tableName} table" } }

    fun findJwtRevocationBoundaries(): Map<UUID, Instant> =
        transaction {
            AppUserTable
                .select(AppUserTable.id, AppUserTable.jwtRevokedAt)
                .where { AppUserTable.jwtRevokedAt.isNotNull() }
                .associate { row ->
                    row[AppUserTable.id] to row[AppUserTable.jwtRevokedAt]!!.toInstant()
                }
        }.also { logger.info { "[FIND-JWT-REVOCATIONS] Fetched ${it.size} persisted revocation boundary(ies)" } }

    /**
     * Sets INACTIVE status and stamps [AppUserTable.deactivatedAt]. Idempotent for
     * already-INACTIVE users — no update and no audit row are written, so a retry
     * never resets the "deactivated X ago" timestamp. Returns null when the user
     * does not exist.
     */
    fun deactivate(
        userId: UUID,
        auditFn: (AppUser, AppUser) -> Unit = { _, _ -> },
    ): AppUser? {
        var changed = false
        val user =
            transaction {
                val beforeRow =
                    AppUserTable
                        .selectAll()
                        .where { AppUserTable.id eq userId }
                        .singleOrNull() ?: return@transaction null

                if (beforeRow[AppUserTable.status] == UserStatus.INACTIVE) {
                    return@transaction beforeRow.toAppUser()
                }

                AppUserTable.update({ AppUserTable.id eq userId }) {
                    it[status] = UserStatus.INACTIVE
                    it[deactivatedAt] = CurrentTimestampWithTimeZone
                    it[jwtRevokedAt] = CurrentTimestampWithTimeZone
                }

                val afterRow =
                    AppUserTable
                        .selectAll()
                        .where { AppUserTable.id eq userId }
                        .single()
                val after = afterRow.toAppUser()
                auditFn(beforeRow.toAppUser(), after)
                changed = true
                after
            }
        logger.info { "[DEACTIVATE] User ${userId.toString().maskUUID()} deactivated=${user != null && changed}" }
        return user
    }

    /**
     * Reverses [deactivate]: INACTIVE → ACTIVE and clears [AppUserTable.deactivatedAt].
     * Idempotent for already-ACTIVE users — no update and no audit row are written
     * (the status did not change), so repeated retries are harmless. Returns null
     * only when the user does not exist.
     */
    fun reactivate(
        userId: UUID,
        auditFn: (AppUser, AppUser) -> Unit = { _, _ -> },
    ): AppUser? {
        var changed = false
        val user =
            transaction {
                val beforeRow =
                    AppUserTable
                        .selectAll()
                        .where { AppUserTable.id eq userId }
                        .singleOrNull() ?: return@transaction null

                if (beforeRow[AppUserTable.status] == UserStatus.ACTIVE) {
                    return@transaction beforeRow.toAppUser()
                }

                AppUserTable.update({ AppUserTable.id eq userId }) {
                    it[status] = UserStatus.ACTIVE
                    it[deactivatedAt] = null
                }

                val afterRow =
                    AppUserTable
                        .selectAll()
                        .where { AppUserTable.id eq userId }
                        .single()
                val after = afterRow.toAppUser()
                auditFn(beforeRow.toAppUser(), after)
                changed = true
                after
            }
        logger.info { "[REACTIVATE] User ${userId.toString().maskUUID()} reactivated=${user != null && changed}" }
        return user
    }

    fun findAll(): List<AppUser> =
        transaction {
            AppUserTable
                .selectAll()
                .orderBy(
                    AppUserTable.displayName to SortOrder.ASC,
                    AppUserTable.username to SortOrder.ASC,
                ).map { it.toAppUser() }
        }.also { logger.info { "[FIND-ALL-USERS] Fetched ${it.size} user(s)" } }

    /** Active assignments (ended_at IS NULL) with branch names, slot ASC — the user-list join. */
    fun findActiveAssignmentsWithBranch(): List<UserBranchAssignmentSummary> =
        transaction {
            UserBranchAssignmentTable
                .innerJoin(BranchTable, { UserBranchAssignmentTable.branchId }, { BranchTable.id })
                .selectAll()
                .where { UserBranchAssignmentTable.endedAt.isNull() }
                .orderBy(
                    UserBranchAssignmentTable.slot to SortOrder.ASC,
                    UserBranchAssignmentTable.userId to SortOrder.ASC,
                ).map { row ->
                    UserBranchAssignmentSummary(
                        userId = row[UserBranchAssignmentTable.userId],
                        branchId = row[UserBranchAssignmentTable.branchId],
                        branchName = row[BranchTable.name],
                        slot = row[UserBranchAssignmentTable.slot],
                    )
                }
        }.also { logger.info { "[FIND-ACTIVE-ASSIGNMENTS-WITH-BRANCH] Fetched ${it.size} active assignment(s)" } }

    fun authorize(id: String): Boolean {
        // safe: id is non-null String; caller guards null before calling
        val userId = UUID.fromString(id)
        val authorized =
            transaction {
                AppUserTable
                    .select(AppUserTable.id)
                    .where { (AppUserTable.id eq userId) and (AppUserTable.status eq UserStatus.ACTIVE) }
                    .empty()
                    .not()
            }
        if (authorized) {
            logger.info { "[AUTHORIZE] User ${id.maskUUID()} is authorized" }
        } else {
            logger.warn { "[AUTHORIZE] User ${id.maskUUID()} is not authorized" }
        }
        return authorized
    }

    private fun ResultRow.toAppUser(): AppUser =
        AppUser(
            id = this[AppUserTable.id].toString(),
            username = this[AppUserTable.username],
            passwordHash = this[AppUserTable.passwordHash],
            status = this[AppUserTable.status],
            displayName = this[AppUserTable.displayName],
            deactivatedAt = this[AppUserTable.deactivatedAt],
            jwtRevokedAt = this[AppUserTable.jwtRevokedAt],
        )
}
