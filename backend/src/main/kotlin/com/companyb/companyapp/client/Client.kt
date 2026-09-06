package com.companyb.companyapp.client

import com.companyb.companyapp.domain.Gender
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Client(
    val id: UUID,
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
)

private const val PHONE_COLUMN_WIDTH = 20

/** Empty address representation (#523): clears reset here, matching create + column default. */
const val DEFAULT_CLIENT_ADDRESS = "N/A"

internal object ClientTable : Table("client") {
    val id = javaUUID("id").autoGenerate()
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val middleName = text("middle_name").nullable()
    val suffix = text("suffix").nullable()
    val phoneNumber = varchar("phone_number", PHONE_COLUMN_WIDTH).nullable()
    val address = text("address").nullable().default(DEFAULT_CLIENT_ADDRESS)
    val gender = varchar("gender", 1)
    val age = integer("age")
    val systolicBp = short("systolic_bp").nullable()
    val diastolicBp = short("diastolic_bp").nullable()
    val medicalConditions = text("medical_conditions").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Client): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            // #525 field policy — every externally writable client field is a value
            // diff so business changes cannot become empty diffs. Names/phone/address
            // are identifying (redacted on anonymize); BP/conditions are health values
            // redacted on anonymize so the audit never becomes a second PII store.
            // Gender/age are retained demographics. deletedAt is excluded: the audit
            // row's own changedAt already marks the anonymization event.
            "firstName" to entity.firstName,
            "lastName" to entity.lastName,
            "middleName" to entity.middleName,
            "suffix" to entity.suffix,
            "phoneNumber" to entity.phoneNumber,
            "address" to entity.address,
            "gender" to entity.gender.name,
            "age" to entity.age.toString(),
            "systolicBp" to entity.systolicBp?.toString(),
            "diastolicBp" to entity.diastolicBp?.toString(),
            "medicalConditions" to entity.medicalConditions,
        )
}
