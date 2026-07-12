package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Client(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String,
    val gender: String,
    val age: Int,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
    val deletedAt: OffsetDateTime?,
)

private const val PHONE_COLUMN_WIDTH = 20

object ClientTable : Table("client") {
    val id = uuid("id").autoGenerate()
    val firstName = text("first_name")
    val lastName = text("last_name")
    val middleName = text("middle_name").nullable()
    val suffix = text("suffix").nullable()
    val phoneNumber = varchar("phone_number", PHONE_COLUMN_WIDTH).nullable()
    val address = text("address").default("N/A")
    val gender = varchar("gender", 1)
    val age = integer("age")
    val systolicBp = short("systolic_bp").nullable()
    val diastolicBp = short("diastolic_bp").nullable()
    val medicalConditions = text("medical_conditions").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
