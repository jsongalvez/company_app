package com.companyb.companyapp.identity

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.RegistrationConflictException
import com.companyb.companyapp.exception.RegistrationConflictField
import com.companyb.companyapp.identity.AppUser
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger { }

private const val MANAGER_ROLE = "MANAGER"

/** Active assignment projected with its branch name — the user-list shape. */
data class UserBranchAssignmentSummary(
    val assignmentId: UUID,
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

/** Before/after status projection for deactivate/reactivate (#323): `changed=false` marks the idempotent no-op skip. */
data class UserStatusTransition(
    val before: AppUser,
    val after: AppUser,
    val changed: Boolean,
)

@Suppress("UnreachableCode", "TooManyFunctions")
internal object UserRepository {
    private val clockTimestamp = CustomFunction("clock_timestamp", CurrentTimestampWithTimeZone.columnType)

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
                        status = row[AppUserTable.status],
                        displayName = row[AppUserTable.displayName],
                        deactivatedAt = row[AppUserTable.deactivatedAt],
                        jwtRevokedAt = row[AppUserTable.jwtRevokedAt],
                        credentialVersion = row[AppUserTable.credentialVersion],
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

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun createUserInTransaction(params: UserCreateParams): UUID {
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
        return insert[AppUserTable.id]
    }

    fun existsById(userId: UUID): Boolean =
        transaction {
            AppUserTable
                .select(AppUserTable.id)
                .where { AppUserTable.id eq userId }
                .empty()
                .not()
        }

    /**
     * Display names for notification copy (#358, merged from the retired `UserDisplayNames`
     * helper): relief broadcast messages name the people in them. Keeps its transaction
     * wrapper (ADR-0024 rule 6); called inside a command it joins the ambient transaction.
     */
    fun findDisplayNamesByIds(ids: Collection<UUID>): Map<UUID, String> {
        if (ids.isEmpty()) return emptyMap()
        return transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id inList ids }
                .associate { it[AppUserTable.id] to it[AppUserTable.displayName] }
        }
    }

    /**
     * In-transaction duplicate lookup for the invite-mint re-invite path (#350): matches on
     * username OR email so either conflict field can resolve to a recoverable account.
     */
    fun findByUsernameOrEmailInTransaction(
        username: String,
        email: String,
    ): ExistingUser? =
        AppUserTable
            .select(AppUserTable.id, AppUserTable.email)
            .where {
                (AppUserTable.username eq username) or (AppUserTable.email eq email)
            }.singleOrNull()
            ?.let {
                ExistingUser(
                    id = it[AppUserTable.id],
                    email = it[AppUserTable.email],
                )
            }

    /** Minimal projection for existence/re-invite checks — no credential material. */
    data class ExistingUser(
        val id: UUID,
        val email: String,
    )

    /** Store half of the accept-invite password set (#350). Runs on the caller's command transaction. */
    fun setPasswordHashInTransaction(
        userId: UUID,
        passwordHash: String,
    ): Long {
        val locked =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: error("User not found")
        val nextVersion = locked[AppUserTable.credentialVersion] + 1
        AppUserTable.update({ AppUserTable.id eq userId }) {
            it[AppUserTable.passwordHash] = passwordHash
            it[AppUserTable.credentialVersion] = nextVersion
        }
        return nextVersion
    }

    /**
     * Login revalidation (#505): runs on the login command's transaction after the
     * expensive bcrypt check. Locks the account row, then proves the verified hash is
     * still current and the account is ACTIVE. Returns the live version, or null when
     * the credential rotated, the account deactivated, or the row vanished.
     */
    fun revalidateCredentialLockedInTransaction(
        userId: UUID,
        verifiedHash: String,
    ): Long? {
        val locked =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: return null
        if (locked[AppUserTable.status] != UserStatus.ACTIVE) return null
        if (locked[AppUserTable.passwordHash] != verifiedHash) return null
        return locked[AppUserTable.credentialVersion]
    }

    /** Current persisted revocation boundary for [userId], read under the command's lock. */
    fun revocationBoundaryInTransaction(userId: UUID): OffsetDateTime? =
        AppUserTable
            .select(AppUserTable.jwtRevokedAt)
            .where { AppUserTable.id eq userId }
            .singleOrNull()
            ?.get(AppUserTable.jwtRevokedAt)

    /** DB clock read for version-bound token issuance (#505) — runs on the caller's command transaction. */
    fun dbNowInTransaction(): OffsetDateTime =
        AppUserTable
            .select(clockTimestamp)
            .first()[clockTimestamp]

    /**
     * Advances the persisted JWT revocation boundary to the database clock time taken after
     * acquiring the user-row lock (#492). Retains the maximum boundary so concurrent
     * reset/logout/deactivation cannot move it backward. Runs on the caller's command
     * transaction and returns the stored boundary.
     */
    fun advanceRevocationBoundaryInTransaction(userId: UUID): OffsetDateTime {
        val locked =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: error("User not found")
        val now =
            AppUserTable
                .select(clockTimestamp)
                .first()[clockTimestamp]
        val existing = locked[AppUserTable.jwtRevokedAt]
        val boundary = if (existing == null || now.isAfter(existing)) now else existing
        if (existing == null || boundary.isAfter(existing)) {
            AppUserTable.update({ AppUserTable.id eq userId }) {
                it[jwtRevokedAt] = boundary
            }
        }
        return boundary
    }

    /**
     * Sets INACTIVE status and stamps [AppUserTable.deactivatedAt]. Idempotent for
     * already-INACTIVE users — no update and no audit row are written (`changed=false`),
     * so a retry never resets the "deactivated X ago" timestamp. Returns null when the
     * user does not exist.
     */
    fun deactivateInTransaction(userId: UUID): UserStatusTransition? {
        val beforeRow =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull() ?: return null

        if (beforeRow[AppUserTable.status] == UserStatus.INACTIVE) {
            val before = beforeRow.toAppUser()
            return UserStatusTransition(before, before, changed = false)
        }

        val now =
            AppUserTable
                .select(clockTimestamp)
                .first()[clockTimestamp]
        val existingBoundary = beforeRow[AppUserTable.jwtRevokedAt]
        val boundary = if (existingBoundary == null || now.isAfter(existingBoundary)) now else existingBoundary
        AppUserTable.update({ AppUserTable.id eq userId }) {
            it[status] = UserStatus.INACTIVE
            it[deactivatedAt] = boundary
            it[jwtRevokedAt] = boundary
        }

        val after =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()
                .toAppUser()
        return UserStatusTransition(beforeRow.toAppUser(), after, changed = true)
    }

    /**
     * Reverses [deactivateInTransaction]: INACTIVE → ACTIVE and clears [AppUserTable.deactivatedAt].
     * Idempotent for already-ACTIVE users — no update and no audit row are written
     * (`changed=false`; the status did not change), so repeated retries are harmless.
     * Returns null only when the user does not exist.
     */
    fun reactivateInTransaction(userId: UUID): UserStatusTransition? {
        val beforeRow =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .singleOrNull() ?: return null

        if (beforeRow[AppUserTable.status] == UserStatus.ACTIVE) {
            val before = beforeRow.toAppUser()
            return UserStatusTransition(before, before, changed = false)
        }

        AppUserTable.update({ AppUserTable.id eq userId }) {
            it[status] = UserStatus.ACTIVE
            it[deactivatedAt] = null
        }

        val after =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()
                .toAppUser()
        return UserStatusTransition(beforeRow.toAppUser(), after, changed = true)
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

    /** Locks target user row for eligibility and concurrent role replacement decisions. */
    fun acquireLockInTransaction(userId: UUID): Boolean =
        AppUserTable
            .selectAll()
            .where { AppUserTable.id eq userId }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull() != null

    /** In-transaction eligibility check for medical-mission delegates. Locks target for pair uniqueness. */
    fun isActiveManagerInTransaction(userId: UUID): Boolean {
        if (!acquireLockInTransaction(userId)) return false
        return AppUserTable
            .innerJoin(UserRoleTable, { AppUserTable.id }, { UserRoleTable.userId })
            .innerJoin(RoleTable, { UserRoleTable.roleId }, { RoleTable.id })
            .selectAll()
            .where {
                (AppUserTable.id eq userId) and
                    (AppUserTable.status eq UserStatus.ACTIVE) and
                    (RoleTable.name eq MANAGER_ROLE)
            }.empty()
            .not()
    }

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
                        assignmentId = row[UserBranchAssignmentTable.id],
                        userId = row[UserBranchAssignmentTable.userId],
                        branchId = row[UserBranchAssignmentTable.branchId],
                        branchName = row[BranchTable.name],
                        slot = row[UserBranchAssignmentTable.slot],
                    )
                }
        }.also { logger.info { "[FIND-ACTIVE-ASSIGNMENTS-WITH-BRANCH] Fetched ${it.size} active assignment(s)" } }

    /**
     * Token-aware authorization read (#492, #505): one query evaluates ACTIVE status,
     * the credential generation, plus the persisted revocation boundary. A token is
     * accepted iff its owner exists, is ACTIVE, carries the current generation, and
     * was issued strictly after that user's nullable boundary. Same-second issuance
     * stays denied — JWT `iat` is second-precision and cannot distinguish before/after.
     */
    fun authorize(
        userId: UUID,
        tokenIssuedAt: Instant,
        credentialVersion: Long = 0L,
    ): Boolean {
        val authorized =
            transaction {
                val row =
                    AppUserTable
                        .select(
                            AppUserTable.status,
                            AppUserTable.jwtRevokedAt,
                            AppUserTable.credentialVersion,
                        ).where { AppUserTable.id eq userId }
                        .singleOrNull() ?: return@transaction false
                if (row[AppUserTable.status] != UserStatus.ACTIVE) return@transaction false
                if (row[AppUserTable.credentialVersion] != credentialVersion) return@transaction false
                val boundary = row[AppUserTable.jwtRevokedAt]?.toInstant() ?: return@transaction true
                tokenIssuedAt.isAfter(boundary)
            }
        if (authorized) {
            logger.info { "[AUTHORIZE] User ${userId.toString().maskUUID()} is authorized" }
        } else {
            logger.warn { "[AUTHORIZE] User ${userId.toString().maskUUID()} is not authorized" }
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
            credentialVersion = this[AppUserTable.credentialVersion],
        )
}
