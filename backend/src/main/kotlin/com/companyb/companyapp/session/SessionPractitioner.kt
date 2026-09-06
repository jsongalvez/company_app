package com.companyb.companyapp.session

import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

data class SessionPractitioner(
    val id: UUID,
    val sessionId: UUID,
    val practitionerId: UUID,
    val remarks: String?,
    val slotAtTime: Short,
)

internal object SessionPractitionerTable : Table("session_practitioner") {
    val id = javaUUID("id").autoGenerate()
    val sessionId = javaUUID("session_id").references(SessionTable.id)
    val practitionerId = javaUUID("practitioner_id").references(AppUserTable.id)
    val remarks = text("remarks").nullable()
    val slotAtTime = short("slot_at_time")

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: SessionPractitioner): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "sessionId" to entity.sessionId.toString(),
            "practitionerId" to entity.practitionerId.toString(),
            "remarks" to entity.remarks,
            "slotAtTime" to entity.slotAtTime.toString(),
        )
}
