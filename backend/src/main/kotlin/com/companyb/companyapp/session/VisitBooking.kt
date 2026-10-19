package com.companyb.companyapp.session

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.session.BookingStatus
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** A client expected at a branch on a future day (feedback e4ec363c). */
data class VisitBooking(
    val id: UUID,
    val branchId: UUID,
    val clientId: UUID,
    val visitDate: LocalDate,
    val visitTime: LocalTime?,
    val note: String?,
    val status: BookingStatus,
)

internal object VisitBookingTable : Table("visit_booking") {
    private const val STATUS_LENGTH = 20

    val id = javaUUID("id")
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val clientId = javaUUID("client_id").references(ClientTable.id)
    val visitDate = date("visit_date")
    val visitTime = time("visit_time").nullable()
    val note = text("note").nullable()
    val status = varchar("status", STATUS_LENGTH)
    val createdBy = javaUUID("created_by").references(AppUserTable.id)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedBy = javaUUID("updated_by").references(AppUserTable.id).nullable()
    val updatedAt = timestampWithTimeZone("updated_at").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: VisitBooking): Map<String, String> =
        buildMap {
            put("id", entity.id.toString())
            put("branchId", entity.branchId.toString())
            put("clientId", entity.clientId.toString())
            put("visitDate", entity.visitDate.toString())
            entity.visitTime?.let { put("visitTime", it.toString()) }
            entity.note?.let { put("note", it) }
            put("status", entity.status.name)
        }
}
