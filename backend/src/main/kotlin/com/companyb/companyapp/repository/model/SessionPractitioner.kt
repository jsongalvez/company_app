package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import java.util.UUID

data class SessionPractitioner(
    val id: UUID,
    val sessionId: UUID,
    val practitionerId: UUID,
    val remarks: String?,
    val slotAtTime: Short,
)

object SessionPractitionerTable : Table("session_practitioner") {
    val id = uuid("id").autoGenerate()
    val sessionId = uuid("session_id").references(SessionTable.id)
    val practitionerId = uuid("practitioner_id").references(AppUserTable.id)
    val remarks = text("remarks").nullable()
    val slotAtTime = short("slot_at_time")

    override val primaryKey = PrimaryKey(id)
}
