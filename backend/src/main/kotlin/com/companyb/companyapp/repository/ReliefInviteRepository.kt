package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.repository.model.ActiveUserCapabilitiesView
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.ReliefInvite
import com.companyb.companyapp.repository.model.ReliefInviteTable
import com.companyb.companyapp.repository.model.ReliefInviteView
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Suppress("TooManyFunctions")
object ReliefInviteRepository {
    /**
     * Inserts a PENDING invite. `insertIgnore` absorbs the partial-unique-index race
     * (`idx_one_pending_accepted_invite` — a concurrent create for the same
     * (invitee, branch_day_id)); `insertedCount == 0` means the index swallowed the
     * insert — a conflict, never a success (the count-0 discipline: the swallowed
     * constraint class here is exactly the per-person guard, and the read-back by id
     * can only miss if the insert was swallowed).
     */
    fun insert(
        id: UUID,
        branchDayId: UUID,
        invitedBy: UUID,
        invitee: UUID,
        auditFn: (ReliefInvite) -> Unit = {},
    ): Pair<ReliefInvite?, Boolean> =
        transaction {
            val insertedCount =
                ReliefInviteTable
                    .insertIgnore {
                        it[ReliefInviteTable.id] = id
                        it[ReliefInviteTable.branchDayId] = branchDayId
                        it[ReliefInviteTable.invitedBy] = invitedBy
                        it[ReliefInviteTable.invitee] = invitee
                    }.insertedCount
            val isNew = insertedCount > 0

            val row = findByIdInTransaction(id)
            if (isNew && row != null) {
                auditFn(row)
            }
            row to isNew
        }

    fun findById(id: UUID): ReliefInvite? =
        transaction {
            findByIdInTransaction(id)
        }

    fun hasPendingOrAcceptedInvite(
        invitee: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            ReliefInviteTable
                .selectAll()
                .where {
                    (ReliefInviteTable.invitee eq invitee) and
                        (ReliefInviteTable.branchDayId eq branchDayId) and
                        (
                            ReliefInviteTable.status inList
                                listOf(ReliefInviteStatus.PENDING, ReliefInviteStatus.ACCEPTED)
                        )
                }.empty()
                .not()
        }

    /**
     * True when [userId] holds an ACTIVE day-scoped grant for [branchDayId] — the
     * GRANTED leg of the per-person create guard (#159 Q3: exclude anyone already
     * PENDING/ACCEPTED/GRANTED for the day). Served by the capabilities view, so
     * INACTIVE users and out-of-window grants are already excluded.
     */
    fun hasActiveGrant(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).selectAll()
                .where {
                    (ActiveUserCapabilitiesView.userId eq userId) and
                        (CapabilityTable.code eq CapabilityCodes.EDIT_BRANCH_DATA) and
                        (ActiveUserCapabilitiesView.contextType eq CapabilityContextType.BRANCH_DAY) and
                        (ActiveUserCapabilitiesView.contextId eq branchDayId)
                }.empty()
                .not()
        }

    /**
     * Invitees with a live (PENDING/ACCEPTED) invite for the day — the candidate-search
     * exclusion set (the same guard as [hasPendingOrAcceptedInvite], batched).
     */
    fun findLiveInviteeIds(branchDayId: UUID): Set<UUID> =
        transaction {
            ReliefInviteTable
                .select(ReliefInviteTable.invitee)
                .where {
                    (ReliefInviteTable.branchDayId eq branchDayId) and
                        (
                            ReliefInviteTable.status inList
                                listOf(ReliefInviteStatus.PENDING, ReliefInviteStatus.ACCEPTED)
                        )
                }.map { it[ReliefInviteTable.invitee] }
                .toSet()
        }

    /**
     * Users holding an ACTIVE day-scoped grant for the day — the GRANTED leg of the
     * candidate exclusion (view-served: inactive + expired excluded).
     */
    fun findActiveGrantUserIds(branchDayId: UUID): Set<UUID> =
        transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).select(ActiveUserCapabilitiesView.userId)
                .where {
                    (CapabilityTable.code eq CapabilityCodes.EDIT_BRANCH_DATA) and
                        (ActiveUserCapabilitiesView.contextType eq CapabilityContextType.BRANCH_DAY) and
                        (ActiveUserCapabilitiesView.contextId eq branchDayId)
                }.map { it[ActiveUserCapabilitiesView.userId] }
                .toSet()
        }

    /** ACTIVE users whose username or displayName starts with [prefix] (case-insensitive). */
    fun findActiveUsersWithPrefix(prefix: String): List<ReliefCandidate> =
        transaction {
            val needle = "${escapeLike(prefix)}%"
            AppUserTable
                .selectAll()
                .where {
                    (AppUserTable.status eq UserStatus.ACTIVE) and
                        (
                            ilike(AppUserTable.username, needle) or
                                ilike(AppUserTable.displayName, needle)
                        )
                }.orderBy(
                    AppUserTable.displayName to SortOrder.ASC,
                    AppUserTable.username to SortOrder.ASC,
                ).map { row ->
                    ReliefCandidate(
                        id = row[AppUserTable.id],
                        username = row[AppUserTable.username],
                        displayName = row[AppUserTable.displayName],
                    )
                }
        }

    /** Escapes LIKE wildcards so a user-typed prefix cannot broaden the match. */
    private fun escapeLike(input: String): String = input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private class ILikeOp(
        expr1: org.jetbrains.exposed.v1.core.Expression<*>,
        expr2: org.jetbrains.exposed.v1.core.Expression<*>,
    ) : org.jetbrains.exposed.v1.core.ComparisonOp(expr1, expr2, "ILIKE")

    @Suppress("UNCHECKED_CAST")
    private fun <T : String?> ilike(
        col: org.jetbrains.exposed.v1.core.Column<T>,
        pattern: String,
    ): org.jetbrains.exposed.v1.core.Op<Boolean> =
        ILikeOp(
            col,
            org.jetbrains.exposed.v1.core.QueryParameter(
                pattern,
                col.columnType as org.jetbrains.exposed.v1.core.IColumnType<String>,
            ),
        )

    fun isActiveUser(userId: UUID): Boolean =
        transaction {
            AppUserTable
                .selectAll()
                .where { (AppUserTable.id eq userId) and (AppUserTable.status eq UserStatus.ACTIVE) }
                .empty()
                .not()
        }

    /** Received invites (caller = invitee): PENDING first, then newest first. */
    fun findReceivedByInvitee(invitee: UUID): List<ReliefInviteView> =
        transaction {
            joinWithDisplay()
                .selectAll()
                .where { ReliefInviteTable.invitee eq invitee }
                .map { it.toView() }
                .withInviteeNames()
                .sortedWith(
                    compareBy<ReliefInviteView> {
                        if (it.invite.status == ReliefInviteStatus.PENDING) 0 else 1
                    }.thenByDescending { it.invite.createdAt },
                )
        }

    /** Sent invites: the caller's own, scoped to the branch path param (parent-child). */
    fun findSentByInviterAndBranch(
        invitedBy: UUID,
        branchId: UUID,
    ): List<ReliefInviteView> =
        transaction {
            joinWithDisplay()
                .selectAll()
                .where {
                    (ReliefInviteTable.invitedBy eq invitedBy) and
                        (BranchDayTable.branchId eq branchId)
                }.orderBy(ReliefInviteTable.createdAt to SortOrder.DESC)
                .map { it.toView() }
                .withInviteeNames()
        }

    fun findViewByInviteId(id: UUID): ReliefInviteView? =
        transaction {
            joinWithDisplay()
                .selectAll()
                .where { ReliefInviteTable.id eq id }
                .singleOrNull()
                ?.toView()
                ?.withInviteeName()
        }

    /** Batch-fills [ReliefInviteView.inviteeName] (one IN-lookup per list, human-scale). */
    private fun List<ReliefInviteView>.withInviteeNames(): List<ReliefInviteView> {
        val ids = map { it.invite.invitee }.toSet()
        if (ids.isEmpty()) return this
        val names =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id inList ids }
                .associate { it[AppUserTable.id] to it[AppUserTable.displayName] }
        return map { it.copy(inviteeName = names[it.invite.invitee] ?: "") }
    }

    private fun ReliefInviteView.withInviteeName(): ReliefInviteView {
        val name =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq invite.invitee }
                .singleOrNull()
                ?.get(AppUserTable.displayName)
        return copy(inviteeName = name ?: "")
    }

    /**
     * Atomic status transition for decline/retract: only a PENDING row moves (the
     * WHERE carries the status, so a concurrent response wins and this update hits
     * 0 rows → null → the service 409s). `respondedAt` stamps the DB clock.
     */
    fun respond(
        id: UUID,
        status: ReliefInviteStatus,
        auditFn: (ReliefInvite, ReliefInvite) -> Unit = { _, _ -> },
    ): ReliefInvite? =
        transaction {
            val before = findByIdInTransaction(id) ?: return@transaction null
            if (before.status != ReliefInviteStatus.PENDING) return@transaction null
            val updated =
                ReliefInviteTable.update({
                    (ReliefInviteTable.id eq id) and (ReliefInviteTable.status eq ReliefInviteStatus.PENDING)
                }) {
                    it[ReliefInviteTable.status] = status
                    it[ReliefInviteTable.respondedAt] = CurrentTimestampWithTimeZone
                }
            if (updated == 0) return@transaction null
            val after = findByIdInTransaction(id) ?: return@transaction null
            auditFn(before, after)
            after
        }

    /**
     * Atomic accept: PENDING → ACCEPTED + the shared relief-grant capability write in
     * ONE transaction (the grant must not outlive a failed status flip, and the status
     * flip must not commit without the grant). The grant is written via
     * [ReliefAccessRepository.grantReliefCapability] (insertIgnore — a redundant grant
     * stays harmless). Returns null when the invite is not PENDING (concurrent
     * response won, or a stale client).
     */
    fun accept(
        id: UUID,
        invitee: UUID,
        validTo: java.time.OffsetDateTime,
        auditFn: (ReliefInvite, ReliefInvite) -> Unit = { _, _ -> },
    ): ReliefInvite? =
        transaction {
            val before = findByIdInTransaction(id) ?: return@transaction null
            if (before.status != ReliefInviteStatus.PENDING) return@transaction null
            val updated =
                ReliefInviteTable.update({
                    (ReliefInviteTable.id eq id) and (ReliefInviteTable.status eq ReliefInviteStatus.PENDING)
                }) {
                    it[ReliefInviteTable.status] = ReliefInviteStatus.ACCEPTED
                    it[ReliefInviteTable.respondedAt] = CurrentTimestampWithTimeZone
                }
            if (updated == 0) return@transaction null
            ReliefAccessRepository.grantReliefCapability(
                userId = invitee,
                branchDayId = before.branchDayId,
                sourceId = id,
                validTo = validTo,
            )
            val after = findByIdInTransaction(id) ?: return@transaction null
            auditFn(before, after)
            after
        }

    private fun findByIdInTransaction(id: UUID): ReliefInvite? =
        ReliefInviteTable
            .selectAll()
            .where { ReliefInviteTable.id eq id }
            .singleOrNull()
            ?.toReliefInvite()

    private fun joinWithDisplay() =
        ReliefInviteTable
            .innerJoin(BranchDayTable, { ReliefInviteTable.branchDayId }, { BranchDayTable.id })
            .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
            .innerJoin(AppUserTable, { ReliefInviteTable.invitedBy }, { AppUserTable.id })

    private fun org.jetbrains.exposed.v1.core.ResultRow.toView(): ReliefInviteView =
        ReliefInviteView(
            invite = toReliefInvite(),
            branchId = this[BranchDayTable.branchId],
            branchName = this[BranchTable.name],
            date = this[BranchDayTable.date],
            inviterName = this[AppUserTable.displayName],
            inviteeName = "",
        )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toReliefInvite(): ReliefInvite =
        ReliefInvite(
            id = this[ReliefInviteTable.id],
            branchDayId = this[ReliefInviteTable.branchDayId],
            invitedBy = this[ReliefInviteTable.invitedBy],
            invitee = this[ReliefInviteTable.invitee],
            status = this[ReliefInviteTable.status],
            respondedAt = this[ReliefInviteTable.respondedAt],
            createdAt = this[ReliefInviteTable.createdAt],
        )
}

data class ReliefCandidate(
    val id: UUID,
    val username: String,
    val displayName: String,
)
