package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.Gender
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Client(
    override val id: UUID,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String?,
    val gender: Gender,
    val age: Int,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
    val deletedAt: OffsetDateTime?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "firstName" to (firstName ?: "null"),
            "lastName" to (lastName ?: "null"),
        )
}

private const val PHONE_COLUMN_WIDTH = 20

object ClientTable : Table("client") {
    val id = javaUUID("id").autoGenerate()
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val middleName = text("middle_name").nullable()
    val suffix = text("suffix").nullable()
    val phoneNumber = varchar("phone_number", PHONE_COLUMN_WIDTH).nullable()
    val address = text("address").nullable().default("N/A")
    val gender = varchar("gender", 1)
    val age = integer("age")
    val systolicBp = short("systolic_bp").nullable()
    val diastolicBp = short("diastolic_bp").nullable()
    val medicalConditions = text("medical_conditions").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
