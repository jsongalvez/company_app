package com.companyb.companyapp.audit

import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal object AppliedOpTable : Table("applied_op") {
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val opId = text("op_id")
    val status = integer("status")
    val body = text("body")
    val appliedAt = timestampWithTimeZone("applied_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(userId, opId)
}

/** What a save's op id holds when the save arrives. */
internal sealed interface OpClaim {
    /** New, or abandoned halfway: this request runs it. */
    data object Claimed : OpClaim

    /** Taken before: answer this. */
    data class Kept(
        val status: Int,
        val body: String,
    ) : OpClaim

    /** Another copy is running it now. */
    data object Running : OpClaim
}

/** The answers [OpLog] keeps; callers run inside their own transaction. Status 0 marks a save still running. */
internal object AppliedOpStore {
    private const val RUNNING = 0

    fun claimInTransaction(
        userId: UUID,
        opId: String,
        abandonedAfter: Duration,
    ): OpClaim {
        val inserted =
            AppliedOpTable
                .insertIgnore {
                    it[AppliedOpTable.userId] = userId
                    it[AppliedOpTable.opId] = opId
                    it[AppliedOpTable.status] = RUNNING
                    it[AppliedOpTable.body] = ""
                }.insertedCount > 0
        val row =
            if (inserted) {
                null
            } else {
                AppliedOpTable
                    .selectAll()
                    .where { (AppliedOpTable.userId eq userId) and (AppliedOpTable.opId eq opId) }
                    .forUpdate()
                    .singleOrNull()
            }
        val cutoff = Instant.now().minus(abandonedAfter).atOffset(ZoneOffset.UTC)
        return when {
            row == null -> {
                OpClaim.Claimed
            }

            row[AppliedOpTable.status] != RUNNING -> {
                OpClaim.Kept(row[AppliedOpTable.status], row[AppliedOpTable.body])
            }

            row[AppliedOpTable.appliedAt].isAfter(cutoff) -> {
                OpClaim.Running
            }

            else -> {
                AppliedOpTable.update({ (AppliedOpTable.userId eq userId) and (AppliedOpTable.opId eq opId) }) {
                    it[AppliedOpTable.appliedAt] = CurrentTimestampWithTimeZone
                }
                OpClaim.Claimed
            }
        }
    }

    fun keepInTransaction(
        userId: UUID,
        opId: String,
        status: Int,
        body: String,
        keep: Duration,
    ) {
        AppliedOpTable.update({ (AppliedOpTable.userId eq userId) and (AppliedOpTable.opId eq opId) }) {
            it[AppliedOpTable.status] = status
            it[AppliedOpTable.body] = body
        }
        val cutoff = Instant.now().minus(keep).atOffset(ZoneOffset.UTC)
        AppliedOpTable.deleteWhere { AppliedOpTable.appliedAt less cutoff }
    }

    fun releaseInTransaction(
        userId: UUID,
        opId: String,
    ) {
        AppliedOpTable.deleteWhere {
            (AppliedOpTable.userId eq userId) and (AppliedOpTable.opId eq opId) and (AppliedOpTable.status eq RUNNING)
        }
    }
}
