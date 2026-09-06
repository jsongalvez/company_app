package com.companyb.companyapp.repository

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class SessionCreateParams(
    val id: UUID,
    val clientId: UUID,
    val branchDayId: UUID,
    val requestedPractitionerId: UUID?,
    val sessionType: SessionType,
    val isWalkIn: Boolean,
    val basePrice: BigDecimal,
    val finalPrice: BigDecimal,
    val remarks: String?,
    val otherConcerns: String?,
    val nextAppointmentDate: LocalDate?,
    val changedBy: UUID,
)

data class SessionCreateResult(
    val session: Session,
    val created: Boolean,
)

@Suppress("TooManyFunctions")
object SessionRepository {
    fun countPriorNonMedicalMissionSessions(clientId: UUID): Long =
        transaction {
            SessionTable
                .leftJoin(
                    ActiveSessionVoidsView,
                    { SessionTable.id },
                    { ActiveSessionVoidsView.sessionId },
                ).selectAll()
                .where {
                    (SessionTable.clientId eq clientId) and
                        (SessionTable.sessionType neq SessionType.MEDICAL_MISSION) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.count()
        }

    /**
     * #424 — final price of the client's most recent non-MEDICAL_MISSION, non-voided session.
     * Same history definition as [countPriorNonMedicalMissionSessions] (mission sessions never
     * count; voids via `active_session_voids` don't count); null when no such session exists.
     */
    fun findMostRecentPriorSessionFinalPrice(clientId: UUID): BigDecimal? =
        transaction {
            SessionTable
                .leftJoin(
                    ActiveSessionVoidsView,
                    { SessionTable.id },
                    { ActiveSessionVoidsView.sessionId },
                ).selectAll()
                .where {
                    (SessionTable.clientId eq clientId) and
                        (SessionTable.sessionType neq SessionType.MEDICAL_MISSION) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.orderBy(SessionTable.createdAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(SessionTable.finalPrice)
        }

    fun getBranchType(branchId: UUID): BranchType? =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq branchId }
                .singleOrNull()
                ?.let { it[BranchTable.branchType] }
        }

    fun branchDayBelongsToBranch(
        branchDayId: UUID,
        branchId: UUID,
    ): Boolean =
        transaction {
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.id eq branchDayId) and
                        (BranchDayTable.branchId eq branchId)
                }.empty()
                .not()
        }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    @Suppress("ThrowsCount")
    fun createInTransaction(params: SessionCreateParams): SessionCreateResult {
        val existingBeforeLock = findSessionByIdInTransaction(params.id)
        if (existingBeforeLock != null) {
            return idempotentResult(existingBeforeLock, params)
        }

        val client = ClientRepository.acquireLockInTransaction(params.clientId)
        val existingAfterLock = findSessionByIdInTransaction(params.id)
        if (existingAfterLock != null) {
            return idempotentResult(existingAfterLock, params)
        }

        // Keep this atomic with the insert: both the precheck and booked_at use the DB transaction clock.
        if (!params.isWalkIn && params.nextAppointmentDate != null) {
            val bookingDate =
                BranchTable
                    .select(CurrentTimestampWithTimeZone)
                    .first()[CurrentTimestampWithTimeZone]
                    .toLocalDate()
            if (params.nextAppointmentDate.isBefore(bookingDate)) {
                throw ValidationException("Next appointment date cannot be before booking date")
            }
        }

        val hasActive = hasActivePendingSessionInTransaction(params.clientId)
        if (hasActive) {
            throw ConflictException("Client already has an active PENDING session")
        }
        if (client?.deletedAt != null) {
            throw ConflictException("Cannot create a session for an anonymized client")
        }

        val insertedCount =
            SessionTable
                .insertIgnore {
                    it[SessionTable.id] = params.id
                    it[SessionTable.clientId] = params.clientId
                    it[SessionTable.branchDayId] = params.branchDayId
                    if (params.requestedPractitionerId != null) {
                        it[SessionTable.requestedPractitionerId] = params.requestedPractitionerId
                    }
                    it[SessionTable.sessionType] = params.sessionType
                    it[SessionTable.isWalkIn] = params.isWalkIn
                    it[SessionTable.basePrice] = params.basePrice
                    it[SessionTable.finalPrice] = params.finalPrice
                    it[SessionTable.createdBy] = params.changedBy
                    if (params.remarks != null) it[SessionTable.remarks] = params.remarks
                    if (params.otherConcerns != null) it[SessionTable.otherConcerns] = params.otherConcerns
                    if (!params.isWalkIn) it[SessionTable.bookedAt] = CurrentTimestampWithTimeZone
                    if (params.nextAppointmentDate !=
                        null
                    ) {
                        it[SessionTable.nextAppointmentDate] = params.nextAppointmentDate
                    }
                }.insertedCount
        val created = insertedCount > 0
        val session =
            findSessionByIdInTransaction(params.id)
                ?: error("session row not found after idempotent insert for ${params.id}")

        return SessionCreateResult(session, created)
    }

    private fun idempotentResult(
        existing: Session,
        params: SessionCreateParams,
    ): SessionCreateResult {
        // #453 — ownership is transaction-local on the row (created_by); no audit read.
        val sameClient = existing.clientId == params.clientId
        val sameBranchDay = existing.branchDayId == params.branchDayId
        val sameCaller = existing.createdBy == params.changedBy
        if (!sameClient || !sameBranchDay || !sameCaller) {
            throw ConflictException("Session id already belongs to another create request")
        }
        return SessionCreateResult(existing, false)
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — optimistic-version write on the caller's
     * command transaction. The affected-row count is load-bearing (the #149 count-0 misfire
     * lesson): a concurrent commit between the command's version pre-check and this conditional
     * UPDATE matches 0 rows — the command must not read back the OTHER writer's row and serve it
     * as its own success (a silent lost update, ADR-0022).
     */
    fun updateStatusInTransaction(
        sessionId: UUID,
        newStatus: SessionStatus,
        expectedVersion: Int,
    ): Session {
        val updatedCount =
            SessionTable.update({
                (SessionTable.id eq sessionId) and (SessionTable.version eq expectedVersion)
            }) {
                it[SessionTable.sessionStatus] = newStatus
                it[SessionTable.version] = expectedVersion + 1
            }
        if (updatedCount != 1) {
            throw VersionMismatchException(SessionTable.tableName, sessionId)
        }

        return findSessionByIdInTransaction(sessionId)
            ?: error("Session $sessionId not found after status update")
    }

    /**
     * In-transaction store operation (#323, ADR-0024). Count-0 misfire guard (the #149 lesson):
     * 0 affected rows = a concurrent commit won the version — the caller must 409, never read
     * back the other writer's row.
     */
    fun updateFinalPriceInTransaction(
        sessionId: UUID,
        newFinalPrice: BigDecimal,
        expectedVersion: Int,
    ): Session {
        val updatedCount =
            SessionTable.update({
                (SessionTable.id eq sessionId) and (SessionTable.version eq expectedVersion)
            }) {
                it[SessionTable.finalPrice] = newFinalPrice
                it[SessionTable.version] = expectedVersion + 1
            }
        if (updatedCount != 1) {
            throw VersionMismatchException(SessionTable.tableName, sessionId)
        }

        return findSessionByIdInTransaction(sessionId)
            ?: error("Session $sessionId not found after final price update")
    }

    fun findById(id: UUID): Session? =
        transaction {
            findSessionByIdInTransaction(id)
        }

    /** Locks and reads session row on caller's open transaction. */
    fun acquireLockInTransaction(id: UUID): Session? =
        SessionTable
            .selectAll()
            .where { SessionTable.id eq id }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?.toSession()

    /** In-transaction void-state store operation. */
    fun setVoidedInTransaction(
        sessionId: UUID,
        isVoided: Boolean,
    ): Int =
        SessionTable.update({ SessionTable.id eq sessionId }) {
            it[SessionTable.isVoided] = isVoided
        }
}

fun hasActivePendingSessionInTransaction(
    clientId: UUID,
    excludedSessionId: UUID? = null,
): Boolean {
    val condition =
        (SessionTable.clientId eq clientId) and
            (SessionTable.sessionStatus eq SessionStatus.PENDING) and
            (SessionTable.isVoided eq false)
    val scopedCondition = excludedSessionId?.let { condition and (SessionTable.id neq it) } ?: condition
    return SessionTable
        .selectAll()
        .where { scopedCondition }
        .empty()
        .not()
}

/** In-transaction read for command-owned flows — runs on the caller's open transaction. */
fun findSessionByIdInTransaction(id: UUID): Session? =
    SessionTable
        .selectAll()
        .where { SessionTable.id eq id }
        .singleOrNull()
        ?.toSession()

fun org.jetbrains.exposed.v1.core.ResultRow.toSession(): Session =
    Session(
        id = this[SessionTable.id],
        clientId = this[SessionTable.clientId],
        branchDayId = this[SessionTable.branchDayId],
        requestedPractitionerId = this[SessionTable.requestedPractitionerId],
        sessionType = this[SessionTable.sessionType],
        isWalkIn = this[SessionTable.isWalkIn],
        sessionStatus = this[SessionTable.sessionStatus],
        basePrice = this[SessionTable.basePrice],
        finalPrice = this[SessionTable.finalPrice],
        remarks = this[SessionTable.remarks],
        otherConcerns = this[SessionTable.otherConcerns],
        bookedAt = this[SessionTable.bookedAt],
        nextAppointmentDate = this[SessionTable.nextAppointmentDate],
        createdBy = this[SessionTable.createdBy],
        createdAt = this[SessionTable.createdAt],
        version = this[SessionTable.version],
    )
