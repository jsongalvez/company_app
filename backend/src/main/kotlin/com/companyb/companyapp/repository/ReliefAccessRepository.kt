package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.repository.model.UserCapabilityTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

data class GrantWithCapabilityParams(
    val requestId: UUID,
    val grantedBy: UUID,
    val userId: UUID,
    val branchDayId: UUID,
    val sourceId: UUID,
    val validTo: OffsetDateTime?,
    val requestedBy: UUID,
)

/** The shared relief-grant write (#159 Q1): who gets the day, for which day, from which source. */
data class GrantReliefCapabilityParams(
    val userId: UUID,
    val branchDayId: UUID,
    val sourceId: UUID,
    val validTo: OffsetDateTime?,
)

/** Mutation projection for grant/deny (#323): `updated=false` marks a preserved early-return path. */
data class ReliefAccessMutation(
    val before: ReliefAccess,
    val after: ReliefAccess,
    val updated: Boolean,
)

@Suppress("TooManyFunctions")
object ReliefAccessRepository {
    /**
     * The shared relief-grant writer (#159 Q1): inserts the day-scoped capability
     * (EDIT_BRANCH_DATA, BRANCH_DAY, [GrantReliefCapabilityParams.branchDayId], source
     * RELIEF_ACCESS, priority [GrantPriorities.RELIEF_ACCESS], validFrom = now, validTo =
     * [GrantReliefCapabilityParams.validTo]) — the capability write used by BOTH the
     * request flow's grant ([grantInTransaction]) and the invite flow's accept.
     * `insertIgnore` keeps a redundant grant harmless (the #159 Q3 decision: capabilities
     * ≠ assignments).
     *
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction.
     */
    fun grantReliefCapability(params: GrantReliefCapabilityParams): Unit =
        insertReliefCapabilityInTransaction(params.userId, params.branchDayId, params.sourceId, params.validTo)

    private fun insertReliefCapabilityInTransaction(
        userId: UUID,
        branchDayId: UUID,
        sourceId: UUID,
        validTo: OffsetDateTime?,
    ) {
        UserCapabilityTable.insertIgnore {
            it[UserCapabilityTable.userId] = userId
            it[UserCapabilityTable.capabilityId] = reliefCapabilityId()
            it[UserCapabilityTable.contextType] = CapabilityContextType.BRANCH_DAY
            it[UserCapabilityTable.contextId] = branchDayId
            it[UserCapabilityTable.sourceType] = CapabilitySourceType.RELIEF_ACCESS
            it[UserCapabilityTable.sourceId] = sourceId
            it[UserCapabilityTable.validFrom] = CurrentTimestampWithTimeZone
            it[UserCapabilityTable.validTo] = validTo
            it[UserCapabilityTable.priority] = GrantPriorities.RELIEF_ACCESS
        }
    }

    private fun reliefCapabilityId(): UUID =
        checkNotNull(
            CapabilityRepository.findIdByCode(CapabilityCodes.EDIT_BRANCH_DATA),
        ) { "EDIT_BRANCH_DATA capability not found" }

    fun findById(id: UUID): ReliefAccess? =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq id }
                .singleOrNull()
                ?.toReliefAccess()
        }

    /** Caller-relative discovery (#351): rows where the caller is target or requester on one branch day. */
    fun findInvolving(
        userId: UUID,
        branchDayId: UUID,
    ): List<ReliefAccess> =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where {
                    (GrantReliefAccessTable.branchDayId eq branchDayId) and
                        (
                            (GrantReliefAccessTable.targetUser eq userId) or
                                (GrantReliefAccessTable.requestedBy eq userId)
                        )
                }.map { it.toReliefAccess() }
        }

    fun findByRequestedByAndBranchDayId(
        requestedBy: UUID,
        branchDayId: UUID,
        status: ReliefAccessStatus,
    ): ReliefAccess? =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where {
                    (GrantReliefAccessTable.requestedBy eq requestedBy) and
                        (GrantReliefAccessTable.branchDayId eq branchDayId) and
                        (GrantReliefAccessTable.requestStatus eq status)
                }.singleOrNull()
                ?.toReliefAccess()
        }

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction. Windowed-grant atomicity (ADR-0024): the day-row lock, PENDING guard,
     * GRANTED dedup lookup, the conditional status update, and the capability write all
     * stay inside this one store function.
     */
    @Suppress("ReturnCount")
    fun grantInTransaction(params: GrantWithCapabilityParams): ReliefAccessMutation? {
        GrantReliefAccessTable
            .selectAll()
            .where {
                (GrantReliefAccessTable.requestedBy eq params.requestedBy) and
                    (GrantReliefAccessTable.branchDayId eq params.branchDayId)
            }.forUpdate(ForUpdateOption.ForUpdate)
            .toList()

        val before =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq params.requestId }
                .singleOrNull()
                ?.toReliefAccess()

        if (before == null) {
            return null
        }
        if (before.requestStatus != ReliefAccessStatus.PENDING) {
            return ReliefAccessMutation(before, before, updated = false)
        }

        val existingGrant =
            GrantReliefAccessTable
                .selectAll()
                .where {
                    (GrantReliefAccessTable.requestedBy eq params.requestedBy) and
                        (GrantReliefAccessTable.branchDayId eq params.branchDayId) and
                        (GrantReliefAccessTable.requestStatus eq ReliefAccessStatus.GRANTED)
                }.singleOrNull()
                ?.toReliefAccess()

        if (existingGrant != null) {
            return ReliefAccessMutation(existingGrant, existingGrant, updated = false)
        }

        GrantReliefAccessTable
            .update({
                (GrantReliefAccessTable.id eq params.requestId) and
                    (GrantReliefAccessTable.requestStatus eq ReliefAccessStatus.PENDING)
            }) {
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.GRANTED
                it[GrantReliefAccessTable.grantedBy] = params.grantedBy
                it[GrantReliefAccessTable.grantedAt] = CurrentTimestampWithTimeZone
            }

        insertReliefCapabilityInTransaction(
            userId = params.userId,
            branchDayId = params.branchDayId,
            sourceId = params.sourceId,
            validTo = params.validTo,
        )

        val after =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq params.requestId }
                .single()
                .toReliefAccess()

        return ReliefAccessMutation(before, after, updated = true)
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction. The FOR UPDATE row lock + non-PENDING early return stay here so the
     * deny check+write is atomic.
     */
    fun denyInTransaction(requestId: UUID): ReliefAccessMutation {
        val before =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .single()
                .toReliefAccess()

        if (before.requestStatus != ReliefAccessStatus.PENDING) {
            return ReliefAccessMutation(before, before, updated = false)
        }

        GrantReliefAccessTable
            .update({
                (GrantReliefAccessTable.id eq requestId) and
                    (GrantReliefAccessTable.requestStatus eq ReliefAccessStatus.PENDING)
            }) {
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.DENIED
            }

        val after =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .single()
                .toReliefAccess()

        return ReliefAccessMutation(before, after, updated = true)
    }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun insertRequestInTransaction(
        id: UUID,
        branchDayId: UUID,
        requestedBy: UUID,
        targetUser: UUID,
    ): Pair<ReliefAccess, Boolean> {
        val insertedCount =
            GrantReliefAccessTable
                .insertIgnore {
                    it[GrantReliefAccessTable.id] = id
                    it[GrantReliefAccessTable.branchDayId] = branchDayId
                    it[GrantReliefAccessTable.requestedBy] = requestedBy
                    it[GrantReliefAccessTable.targetUser] = targetUser
                }.insertedCount
        val isNew = insertedCount > 0

        val row =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq id }
                .single()
                .toReliefAccess()

        return row to isNew
    }

    /** Read wrapper — see [hasActiveClockInInTransaction] (#354 edge 2). */
    fun hasActiveClockIn(
        targetUser: UUID,
        branchDayId: UUID,
    ): Boolean = transaction { hasActiveClockInInTransaction(targetUser, branchDayId) }

    /**
     * In-transaction presence read (#354 edges 2+4) — runs on the caller's command
     * transaction so eligibility rechecks commit atomically with the write they gate.
     */
    fun hasActiveClockInInTransaction(
        targetUser: UUID,
        branchDayId: UUID,
    ): Boolean =
        AttendanceTable
            .selectAll()
            .where {
                (AttendanceTable.userId eq targetUser) and
                    (AttendanceTable.branchDayId eq branchDayId) and
                    (AttendanceTable.clockOut.isNull())
            }.empty()
            .not()

    /**
     * In-transaction status read (#354 edge 4) — a deactivated user must not remain a live
     * request target; runs on the caller's command transaction.
     */
    fun isActiveUserInTransaction(userId: UUID): Boolean =
        AppUserTable
            .selectAll()
            .where { (AppUserTable.id eq userId) and (AppUserTable.status eq UserStatus.ACTIVE) }
            .empty()
            .not()

    /** Read wrapper — see [isReliefUserInTransaction]. */
    fun isReliefUser(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean = transaction { isReliefUserInTransaction(userId, branchDayId) }

    /** In-transaction relief-eligibility read — runs on the caller's command transaction. */
    fun isReliefUserInTransaction(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        BranchDayAssignmentTable
            .selectAll()
            .where {
                (BranchDayAssignmentTable.userId eq userId) and
                    (BranchDayAssignmentTable.branchDayId eq branchDayId)
            }.singleOrNull()
            ?.let { it[BranchDayAssignmentTable.isRelief] }
            ?: false

    private fun org.jetbrains.exposed.v1.core.ResultRow.toReliefAccess(): ReliefAccess =
        ReliefAccess(
            id = this[GrantReliefAccessTable.id],
            branchDayId = this[GrantReliefAccessTable.branchDayId],
            requestedBy = this[GrantReliefAccessTable.requestedBy],
            requestStatus = this[GrantReliefAccessTable.requestStatus],
            targetUser = this[GrantReliefAccessTable.targetUser],
            grantedBy = this[GrantReliefAccessTable.grantedBy],
            grantedAt = this[GrantReliefAccessTable.grantedAt],
        )
}
