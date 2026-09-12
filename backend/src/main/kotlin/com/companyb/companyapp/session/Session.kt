package com.companyb.companyapp.session

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Session(
    val id: UUID,
    val clientId: UUID,
    val branchDayId: UUID,
    val requestedPractitionerId: UUID?,
    val sessionType: SessionType,
    val isWalkIn: Boolean,
    val sessionStatus: SessionStatus,
    val basePrice: BigDecimal,
    val finalPrice: BigDecimal,
    val remarks: String?,
    val otherConcerns: String?,
    val bookedAt: OffsetDateTime?,
    val nextAppointmentDate: LocalDate?,
    val createdBy: UUID?,
    val createdAt: OffsetDateTime,
    val version: Int,
)

private const val PRECISION = 10
private const val SCALE = 2

/**
 * Largest storable session money amount (#924): session base/final price and the
 * base rate share NUMERIC(10,2) columns with no CHECK, so the pre-gate below owns
 * the bound and violations return 400 instead of an ExposedSQLException 500.
 * Every session money entry point (create/update final price, base-rate set)
 * calls it; basePrice rows inherit the bound transitively through the gated rates.
 */
internal const val SESSION_MONEY_MAX_PLAIN = "99999999.99"

internal val SESSION_MONEY_MAX: BigDecimal = BigDecimal(SESSION_MONEY_MAX_PLAIN)

/**
 * Persisted-range pre-gate (#924): every NUMERIC(10,2) session money write is
 * checked before the insert/update so overflow returns 400 instead of a Postgres
 * numeric overflow 500 + #475 auto-file (the #912/#921/#923 fail-closed class).
 * Callers validate the mission-normalized effective amount, never the raw input —
 * a MEDICAL_MISSION ₱0 normalization must not reject (#405 invariant).
 */
internal fun validateSessionMoney(
    amount: BigDecimal,
    field: String,
) {
    if (amount > SESSION_MONEY_MAX) {
        throw ValidationException("$field must be at most $SESSION_MONEY_MAX_PLAIN")
    }
}

internal object SessionTable : Table("session") {
    val id = javaUUID("id").autoGenerate()
    val clientId = javaUUID("client_id").references(ClientTable.id)
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val requestedPractitionerId = javaUUID("requested_practitioner_id").references(AppUserTable.id).nullable()
    val sessionType =
        customEnumeration<SessionType>(
            name = "session_type",
            sql = "session_type",
            // SAFETY: PG enum column binds as String via customEnumeration #467
            fromDb = { value -> SessionType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "session_type"
                obj.value = it.name
                obj
            },
        )
    val isWalkIn = bool("is_walk_in")
    val sessionStatus =
        customEnumeration<SessionStatus>(
            name = "session_status",
            sql = "session_status",
            // SAFETY: PG enum column binds as String via customEnumeration #467
            fromDb = { value -> SessionStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "session_status"
                obj.value = it.name
                obj
            },
        ).default(SessionStatus.PENDING)
    val isVoided = bool("is_voided").default(false)
    val basePrice = decimal("base_price", PRECISION, SCALE)
    val finalPrice = decimal("final_price", PRECISION, SCALE)
    val remarks = text("remarks").nullable()
    val otherConcerns = text("other_concerns").nullable()
    val bookedAt = timestampWithTimeZone("booked_at").nullable()
    val nextAppointmentDate = date("next_appointment_date").nullable()

    // #453 — transaction-local idempotency owner (Expense created_by precedent).
    // Nullable: backfilled from audit INSERT rows; new rows always write it.
    val createdBy = javaUUID("created_by").references(AppUserTable.id).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val version = integer("version").default(1)

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Session): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "clientId" to entity.clientId.toString(),
            "branchDayId" to entity.branchDayId.toString(),
            // #525 field policy — every stored creation input is a value diff so
            // creation context cannot vanish: requested practitioner, walk-in flag,
            // prices, remarks/concerns, booking and next-appointment dates, and the
            // idempotency owner. createdAt/version are excluded as derived/internal
            // (audit changedAt already marks event time; version would noise every
            // diff). isVoided is excluded: void state is audited via session_void.
            "requestedPractitionerId" to entity.requestedPractitionerId?.toString(),
            "isWalkIn" to entity.isWalkIn.toString(),
            "sessionType" to entity.sessionType.name,
            "sessionStatus" to entity.sessionStatus.name,
            "basePrice" to entity.basePrice.toPlainString(),
            "finalPrice" to entity.finalPrice.toPlainString(),
            "remarks" to entity.remarks,
            "otherConcerns" to entity.otherConcerns,
            "bookedAt" to entity.bookedAt?.toString(),
            "nextAppointmentDate" to entity.nextAppointmentDate?.toString(),
            "createdBy" to entity.createdBy?.toString(),
        )
}
