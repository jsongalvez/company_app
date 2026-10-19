package com.companyb.companyapp.client

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.client.Gender
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentDate
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.LocalDate
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
    /** Age today: the typed-in age plus the full years since [ageEnteredOn]. */
    val age: Int,
    /** The age as typed (what is stored). */
    val ageAsEntered: Int = age,
    /** The day [ageAsEntered] was typed in. */
    val ageEnteredOn: LocalDate = LocalDate.EPOCH,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
    val deletedAt: OffsetDateTime?,
    /** Visits before the app existed; counted toward visit history (sets the visit type). */
    val earlierVisits: Int = 0,
)

private const val PHONE_COLUMN_WIDTH = 20

/**
 * Persisted-field bounds (#923): mirrors the `client` table CHECKs
 * (`V1__full_schema.sql`: age 0–120, systolic 40–300, diastolic 20–200) and
 * the `phone_number` varchar width. Route + service pre-gates reference these
 * so out-of-range input fails closed with 400 before Postgres surfaces a 500.
 */
internal const val CLIENT_AGE_MIN = 0
internal const val CLIENT_AGE_MAX = 120

/** The app's date today (its day changes at 4 AM Manila time). */
internal fun clientToday(): LocalDate = BranchDayService.currentOperationalDate()

/** Age today from the age typed in on [enteredOn]: it goes up by one each year, never past the maximum. */
internal fun ageToday(
    ageAsEntered: Int,
    enteredOn: LocalDate,
    today: LocalDate,
): Int {
    val years =
        java.time.temporal.ChronoUnit.YEARS
            .between(enteredOn, today)
            .toInt()
    return (ageAsEntered + years.coerceAtLeast(0)).coerceAtMost(CLIENT_AGE_MAX)
}

internal const val CLIENT_SYSTOLIC_MIN: Short = 40
internal const val CLIENT_SYSTOLIC_MAX: Short = 300
internal const val CLIENT_DIASTOLIC_MIN: Short = 20
internal const val CLIENT_DIASTOLIC_MAX: Short = 200
internal const val CLIENT_PHONE_MAX_LENGTH = PHONE_COLUMN_WIDTH
internal const val CLIENT_EARLIER_VISITS_MAX = 1000

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
    val ageEnteredOn = date("age_entered_on").defaultExpression(CurrentDate)
    val systolicBp = short("systolic_bp").nullable()
    val diastolicBp = short("diastolic_bp").nullable()
    val medicalConditions = text("medical_conditions").nullable()
    val earlierVisits = integer("earlier_visits").default(0)
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
            "earlierVisits" to entity.earlierVisits.toString(),
        )
}
