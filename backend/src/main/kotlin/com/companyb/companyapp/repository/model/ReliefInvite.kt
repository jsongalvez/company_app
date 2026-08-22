package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.ReliefInviteStatus
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

data class ReliefInvite(
    val id: UUID,
    val branchDayId: UUID,
    val invitedBy: UUID,
    val invitee: UUID,
    val status: ReliefInviteStatus,
    val respondedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

/**
 * Relief invite projection with the row's display fields (branch name, day date,
 * inviter display name) — the shape both the received and sent lists serve.
 */
data class ReliefInviteView(
    val invite: ReliefInvite,
    val branchId: UUID,
    val branchName: String,
    val date: java.time.LocalDate,
    val inviterName: String,
    val inviteeName: String,
)

/**
 * #359 — ACCEPTED invite joined with its duty day and branch: the reminder-job scan row.
 * The duty date is the Branch Day's calendar date; reminders key off it, not respond time.
 */
data class AcceptedInviteWithBranch(
    val inviteId: UUID,
    val invitee: UUID,
    val branchDayId: UUID,
    val branchId: UUID,
    val branchName: String,
    val date: java.time.LocalDate,
)

object ReliefInviteTable : Table("relief_invite") {
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val invitedBy = javaUUID("invited_by").references(AppUserTable.id)
    val invitee = javaUUID("invitee").references(AppUserTable.id)
    val status =
        customEnumeration<ReliefInviteStatus>(
            name = "status",
            sql = "relief_invite_status",
            fromDb = { value -> ReliefInviteStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "relief_invite_status"
                obj.value = it.name
                obj
            },
        ).default(ReliefInviteStatus.PENDING)
    val respondedAt = timestampWithTimeZone("responded_at").nullable()
    val createdAt =
        timestampWithTimeZone("created_at")
            .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: ReliefInvite): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "branchDayId" to entity.branchDayId.toString(),
            "invitedBy" to entity.invitedBy.toString(),
            "invitee" to entity.invitee.toString(),
            "status" to entity.status.name,
            "respondedAt" to (entity.respondedAt?.toString() ?: "null"),
            "createdAt" to entity.createdAt.toString(),
        )
}
