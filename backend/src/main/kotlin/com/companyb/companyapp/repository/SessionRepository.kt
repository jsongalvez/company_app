package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class SessionCreateResult(
    val session: Session,
    val created: Boolean,
)

object SessionRepository {
    fun hasActivePendingSession(clientId: UUID): Boolean =
        transaction {
            SessionTable
                .selectAll()
                .where {
                    (SessionTable.clientId eq clientId) and
                        (SessionTable.sessionStatus eq SessionStatus.PENDING)
                }.empty()
                .not()
        }

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

    fun getBranchType(branchId: UUID): BranchType? =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq branchId }
                .singleOrNull()
                ?.let { it[BranchTable.branchType] }
        }

    @Suppress("LongParameterList")
    fun create(
        id: UUID,
        clientId: UUID,
        branchDayId: UUID,
        requestedPractitionerId: UUID?,
        sessionType: SessionType,
        isWalkIn: Boolean,
        basePrice: BigDecimal,
        finalPrice: BigDecimal,
        remarks: String?,
        otherConcerns: String?,
        bookedAt: OffsetDateTime?,
        nextAppointmentDate: LocalDate?,
        changedBy: UUID,
    ): SessionCreateResult =
        transaction {
            val insertedCount =
                SessionTable
                    .insertIgnore {
                        it[SessionTable.id] = id
                        it[SessionTable.clientId] = clientId
                        it[SessionTable.branchDayId] = branchDayId
                        if (requestedPractitionerId != null) {
                            it[SessionTable.requestedPractitionerId] = requestedPractitionerId
                        }
                        it[SessionTable.sessionType] = sessionType
                        it[SessionTable.isWalkIn] = isWalkIn
                        it[SessionTable.basePrice] = basePrice
                        it[SessionTable.finalPrice] = finalPrice
                        if (remarks != null) it[SessionTable.remarks] = remarks
                        if (otherConcerns != null) it[SessionTable.otherConcerns] = otherConcerns
                        if (bookedAt != null) it[SessionTable.bookedAt] = bookedAt
                        if (nextAppointmentDate != null) it[SessionTable.nextAppointmentDate] = nextAppointmentDate
                    }.insertedCount
            val created = insertedCount > 0
            val session =
                findByIdInTransaction(id) ?: error("session row not found after idempotent insert for $id")

            if (created) {
                AuditLogRepository.record(
                    tableName = SessionTable.tableName,
                    recordId = session.id,
                    action = AuditAction.INSERT,
                    changedBy = changedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "id" to session.id.toString(),
                            "clientId" to session.clientId.toString(),
                            "branchDayId" to session.branchDayId.toString(),
                            "sessionType" to sessionType.name,
                            "finalPrice" to finalPrice.toPlainString(),
                        ),
                )
            }
            SessionCreateResult(session, created)
        }.also {
            logger.info {
                "[CREATE-SESSION] Session ${it.session.id} created=${it.created}"
            }
        }

    fun updateStatus(
        sessionId: UUID,
        oldStatus: SessionStatus,
        newStatus: SessionStatus,
        expectedVersion: Int,
        changedBy: UUID,
    ): Session =
        transaction {
            SessionTable.update({
                (SessionTable.id eq sessionId) and (SessionTable.version eq expectedVersion)
            }) {
                it[SessionTable.sessionStatus] = newStatus
                it[SessionTable.version] = expectedVersion + 1
            }

            val session =
                findByIdInTransaction(sessionId)
                    ?: error("Session $sessionId not found after status update")

            AuditLogRepository.record(
                tableName = SessionTable.tableName,
                recordId = session.id,
                action = AuditAction.UPDATE,
                changedBy = changedBy,
                oldValue = AuditLogRepository.jsonField("sessionStatus", oldStatus.name),
                newValue = AuditLogRepository.jsonField("sessionStatus", newStatus.name),
            )

            session
        }

    fun findById(id: UUID): Session? =
        transaction {
            findByIdInTransaction(id)
        }

    fun updateOtherConcerns(
        sessionId: UUID,
        otherConcerns: String?,
        changedBy: UUID,
    ) {
        transaction {
            val session = findByIdInTransaction(sessionId) ?: error("Session $sessionId not found")

            SessionTable.update({ SessionTable.id eq sessionId }) {
                it[SessionTable.otherConcerns] = otherConcerns
            }

            AuditLogRepository.record(
                tableName = SessionTable.tableName,
                recordId = session.id,
                action = AuditAction.UPDATE,
                changedBy = changedBy,
                oldValue = AuditLogRepository.jsonField("otherConcerns", session.otherConcerns ?: ""),
                newValue = AuditLogRepository.jsonField("otherConcerns", otherConcerns ?: ""),
            )
        }
    }

    private fun findByIdInTransaction(id: UUID): Session? =
        SessionTable
            .selectAll()
            .where { SessionTable.id eq id }
            .singleOrNull()
            ?.toSession()

    private fun org.jetbrains.exposed.sql.ResultRow.toSession(): Session =
        Session(
            id = this[SessionTable.id],
            clientId = this[SessionTable.clientId],
            branchDayId = this[SessionTable.branchDayId],
            requestedPractitionerId = this[SessionTable.requestedPractitionerId],
            sessionType = this.get<com.companyb.companyapp.domain.SessionType>(SessionTable.sessionType).name,
            isWalkIn = this[SessionTable.isWalkIn],
            sessionStatus = this.get<SessionStatus>(SessionTable.sessionStatus).name,
            basePrice = this[SessionTable.basePrice],
            finalPrice = this[SessionTable.finalPrice],
            remarks = this[SessionTable.remarks],
            otherConcerns = this[SessionTable.otherConcerns],
            bookedAt = this[SessionTable.bookedAt],
            nextAppointmentDate = this[SessionTable.nextAppointmentDate],
            createdAt = this[SessionTable.createdAt],
            version = this[SessionTable.version],
        )
}
