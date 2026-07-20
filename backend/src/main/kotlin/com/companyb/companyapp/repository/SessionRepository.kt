package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.Session
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
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
    val bookedAt: OffsetDateTime?,
    val nextAppointmentDate: LocalDate?,
    val changedBy: UUID,
)

data class SessionCreateResult(
    val session: Session,
    val created: Boolean,
)

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

    fun getBranchType(branchId: UUID): BranchType? =
        transaction {
            BranchTable
                .selectAll()
                .where { BranchTable.id eq branchId }
                .singleOrNull()
                ?.let { it[BranchTable.branchType] }
        }

    fun create(
        params: SessionCreateParams,
        auditFn: (Session) -> Unit = {},
    ): SessionCreateResult =
        transaction {
            acquireClientLock(params.clientId)
            val hasActive = hasActivePendingSessionInTransaction(params.clientId)
            if (hasActive) {
                error("client_already_has_pending_session")
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
                        if (params.remarks != null) it[SessionTable.remarks] = params.remarks
                        if (params.otherConcerns != null) it[SessionTable.otherConcerns] = params.otherConcerns
                        if (params.bookedAt != null) it[SessionTable.bookedAt] = params.bookedAt
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

            if (created) {
                auditFn(session)
            }
            SessionCreateResult(session, created)
        }.also {
            logger.info {
                "[CREATE-SESSION] Session ${it.session.id} created=${it.created}"
            }
        }

    @Suppress("LongParameterList", "UNUSED_PARAMETER")
    fun updateStatus(
        sessionId: UUID,
        oldStatus: SessionStatus,
        newStatus: SessionStatus,
        expectedVersion: Int,
        changedBy: UUID,
        auditFn: (Session) -> Unit = {},
    ): Session =
        transaction {
            SessionTable.update({
                (SessionTable.id eq sessionId) and (SessionTable.version eq expectedVersion)
            }) {
                it[SessionTable.sessionStatus] = newStatus
                it[SessionTable.version] = expectedVersion + 1
            }

            val session =
                findSessionByIdInTransaction(sessionId)
                    ?: error("Session $sessionId not found after status update")

            auditFn(session)

            session
        }

    fun findById(id: UUID): Session? =
        transaction {
            findSessionByIdInTransaction(id)
        }

    @Suppress("UNUSED_PARAMETER")
    fun updateOtherConcerns(
        sessionId: UUID,
        otherConcerns: String?,
        changedBy: UUID,
        auditFn: (Session) -> Unit = {},
    ): Session =
        transaction {
            SessionTable.update({ SessionTable.id eq sessionId }) {
                it[SessionTable.otherConcerns] = otherConcerns
            }

            val updated =
                findSessionByIdInTransaction(sessionId)
                    ?: error("Session $sessionId not found after other concerns update")

            auditFn(updated)

            updated
        }

    private fun acquireClientLock(clientId: UUID) {
        // Row-level lock on client to serialize concurrent session creation (CR-018 C2).
        ClientTable
            .selectAll()
            .where { ClientTable.id eq clientId }
            .forUpdate(ForUpdateOption.ForUpdate)
    }

    private fun hasActivePendingSessionInTransaction(clientId: UUID): Boolean =
        SessionTable
            .selectAll()
            .where {
                (SessionTable.clientId eq clientId) and
                    (SessionTable.sessionStatus eq SessionStatus.PENDING)
            }.empty()
            .not()
}

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
