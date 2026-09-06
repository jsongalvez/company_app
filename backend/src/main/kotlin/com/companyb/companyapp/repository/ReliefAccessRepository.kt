package com.companyb.companyapp.repository

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID

data class GrantWithCapabilityParams(
    val requestId: UUID,
    val grantedBy: UUID,
)

/** Mutation projection for grant/deny/cancel (#323): `updated=false` marks a preserved early-return path. */
data class ReliefAccessMutation(
    val before: ReliefAccess,
    val after: ReliefAccess,
    val updated: Boolean,
)

/** A request row joined with its branch-day context — the caller's own-list shape (#357). */
data class ReliefRequestWithBranch(
    val access: ReliefAccess,
    val branchId: UUID,
    val branchName: String,
    val date: LocalDate,
)

@Suppress("TooManyFunctions")
object ReliefAccessRepository {
    fun findById(id: UUID): ReliefAccess? =
        transaction {
            findByIdInTransaction(id)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(id: UUID): ReliefAccess? =
        GrantReliefAccessTable
            .selectAll()
            .where { GrantReliefAccessTable.id eq id }
            .singleOrNull()
            ?.toReliefAccess()

    /** Every request on one branch day — the member surface (#357 broadcast model). */
    fun findByBranchDayId(branchDayId: UUID): List<ReliefAccess> =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.branchDayId eq branchDayId }
                .map { it.toReliefAccess() }
        }

    /** The caller's own requests across all branches/days, newest day first (#357). */
    fun findMine(userId: UUID): List<ReliefRequestWithBranch> =
        transaction {
            GrantReliefAccessTable
                .innerJoin(BranchDayTable, { GrantReliefAccessTable.branchDayId }, { BranchDayTable.id })
                .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
                .selectAll()
                .where { GrantReliefAccessTable.requestedBy eq userId }
                .orderBy(BranchDayTable.date to SortOrder.DESC)
                .map { it.toReliefRequestWithBranch() }
        }

    /**
     * #358 — PENDING requests whose Branch Day has ended (date strictly before the current
     * operational date): the expiry-notice scan set. Status stays PENDING by design (#159 Q6
     * invite precedent); the stored EXPIRED notice is the only expiry marker.
     */
    fun findPendingWithPastDay(beforeDate: LocalDate): List<ReliefRequestWithBranch> =
        transaction {
            GrantReliefAccessTable
                .innerJoin(BranchDayTable, { GrantReliefAccessTable.branchDayId }, { BranchDayTable.id })
                .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
                .selectAll()
                .where {
                    (GrantReliefAccessTable.requestStatus eq ReliefAccessStatus.PENDING) and
                        (BranchDayTable.date less beforeDate)
                }.map { it.toReliefRequestWithBranch() }
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
     * transaction. The FOR UPDATE row lock + PENDING guard + conditional status update
     * stay here so the grant check+write is atomic; the day-scoped grant itself is
     * written by the command through the authorization seam (#538) in the same
     * transaction. #357: the #354 cross-request winner sweep is retired (multiple
     * relief workers per branch-day are allowed — there is nothing left to
     * supersede); a redundant second grant is an idempotent replay via the
     * non-PENDING early return.
     */
    fun grantInTransaction(params: GrantWithCapabilityParams): ReliefAccessMutation? {
        val before =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq params.requestId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .singleOrNull()
                ?.toReliefAccess()

        if (before == null) {
            return null
        }
        if (before.requestStatus != ReliefAccessStatus.PENDING) {
            return ReliefAccessMutation(before, before, updated = false)
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

    /**
     * Withdraw/cancel terminal state (#357): PENDING → CANCELLED, atomic under the same
     * row lock as deny. In-transaction store operation (#323, ADR-0024).
     */
    fun cancelInTransaction(requestId: UUID): ReliefAccessMutation {
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
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.CANCELLED
            }

        val after =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .single()
                .toReliefAccess()

        return ReliefAccessMutation(before, after, updated = true)
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command
     * transaction. Three outcomes under the partial unique index
     * `idx_one_live_relief_request` (#352 Q4 flood control):
     * - `(row, true)` — freshly inserted;
     * - `(row, false)` — the same [id] already existed (an idempotent replay);
     * - `(null, false)` — a different LIVE request stands for (requester, day): the index
     *   swallowed the insert and NO row carries [id], so the service raises the conflict.
     */
    fun insertRequestInTransaction(
        id: UUID,
        branchDayId: UUID,
        requestedBy: UUID,
    ): Pair<ReliefAccess?, Boolean> {
        val insertedCount =
            GrantReliefAccessTable
                .insertIgnore {
                    it[GrantReliefAccessTable.id] = id
                    it[GrantReliefAccessTable.branchDayId] = branchDayId
                    it[GrantReliefAccessTable.requestedBy] = requestedBy
                }.insertedCount

        val row =
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq id }
                .singleOrNull()
                ?.toReliefAccess()

        // insertedCount>0 but no row is impossible; count==0 with no row = the live-sibling
        // swallow (the caller distinguishes replay from conflict via `first == null`).
        return row to (insertedCount > 0)
    }

    /** Read wrapper — see [hasActiveClockInInTransaction]. */
    fun hasActiveClockIn(
        targetUser: UUID,
        branchDayId: UUID,
    ): Boolean = transaction { hasActiveClockInInTransaction(targetUser, branchDayId) }

    /**
     * In-transaction presence read — runs on the caller's command transaction so the
     * retraction lock commits atomically with the cancel it gates (#357: all retraction
     * stops once the requester clocks in at the branch).
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

    /** Read wrapper — see [isActiveUserInTransaction]. */
    fun isActiveUser(userId: UUID): Boolean = transaction { isActiveUserInTransaction(userId) }

    /**
     * In-transaction status read (#354 edge 4) — a deactivated user must not hold a live
     * requester identity; runs on the caller's command transaction.
     */
    fun isActiveUserInTransaction(userId: UUID): Boolean =
        AppUserTable
            .selectAll()
            .where { (AppUserTable.id eq userId) and (AppUserTable.status eq UserStatus.ACTIVE) }
            .empty()
            .not()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toReliefAccess(): ReliefAccess =
        ReliefAccess(
            id = this[GrantReliefAccessTable.id],
            branchDayId = this[GrantReliefAccessTable.branchDayId],
            requestedBy = this[GrantReliefAccessTable.requestedBy],
            requestStatus = this[GrantReliefAccessTable.requestStatus],
            grantedBy = this[GrantReliefAccessTable.grantedBy],
            grantedAt = this[GrantReliefAccessTable.grantedAt],
        )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toReliefRequestWithBranch(): ReliefRequestWithBranch =
        ReliefRequestWithBranch(
            access = toReliefAccess(),
            branchId = this[BranchDayTable.branchId],
            branchName = this[BranchTable.name],
            date = this[BranchDayTable.date],
        )
}
