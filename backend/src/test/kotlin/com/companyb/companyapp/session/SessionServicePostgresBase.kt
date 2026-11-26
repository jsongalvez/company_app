package com.companyb.companyapp.session
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Ids, seed rows and helpers shared by the session service suites. */
abstract class SessionServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val clientId = TestFixtures.uuid()
    protected val sessionId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val rateId = TestFixtures.uuid()
    protected val secondSessionRateId = TestFixtures.uuid()
    protected val subsequentRateId = TestFixtures.uuid()
    protected val walkInSessionId = TestFixtures.uuid()
    protected val practitionerId = TestFixtures.uuid()
    protected val practitionerSessionId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "session-caller")
        IdentityFixtures.insertTestUser(practitionerId, "session-practitioner")

        BranchWorkforceFixtures.insertTestBranch(branchId)

        SessionClientFixtures.insertTestClient(clientId)

        val elsewhere = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(elsewhere)
        IdentityFixtures.member(callerId, elsewhere)

        SessionClientFixtures.insertTestBaseRate(rateId, branchId, callerId)
        SessionClientFixtures.insertTestBaseRate(
            secondSessionRateId,
            branchId,
            callerId,
            SessionType.SECOND_SESSION,
        )
        SessionClientFixtures.insertTestBaseRate(
            subsequentRateId,
            branchId,
            callerId,
            SessionType.SUBSEQUENT,
        )

        insertAssignment(practitionerId)
    }

    @Suppress("LongParameterList") // #593
    protected fun createSession(
        callerId: UUID,
        id: UUID,
        clientId: UUID = this.clientId,
        branchId: UUID = this.branchId,
        isWalkIn: Boolean = false,
        requestedPractitionerId: UUID? = null,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
        gatedBranchDayId: UUID? = null,
        nextAppointmentDate: LocalDate? = null,
        reason: String? = null,
    ) = SessionService.create(
        callerId = callerId,
        NewSession(
            id = id,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = isWalkIn,
            requestedPractitionerId = requestedPractitionerId,
            finalPrice = finalPrice,
            remarks = "Test session",
            otherConcerns = null,
            nextAppointmentDate = nextAppointmentDate,
            reason = reason,
        ),
        context =
            SessionCreateContext(gatedBranchDayId = gatedBranchDayId, requireClockIn = false),
    )

    protected fun insertAssignment(userId: UUID) {
        transaction {
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = userId,
                    branchId = branchId,
                    slot = 1,
                    assignedBy = callerId,
                ),
            )
        }
    }

    protected fun auditEntryCount(
        tableName: String,
        recordId: UUID,
        actionFilter: AuditAction? = null,
    ): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId) and
                        (
                            actionFilter?.let { AuditLogTable.action eq it }
                                ?: AuditLogTable.auditTableName.isNotNull()
                        )
                }.count()
        }

    protected fun insertRemittedDay(): UUID {
        val dayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        return dayId
    }

    protected fun insertSessionOnDay(
        id: UUID,
        dayId: UUID,
        sessionStatus: SessionStatus = SessionStatus.PENDING,
    ) {
        val sessionClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(id, sessionClientId, dayId, sessionStatus = sessionStatus)
    }

    protected fun auditEntry(
        tableName: String,
        recordId: UUID,
    ) = transaction {
        AuditLogTable
            .selectAll()
            .where {
                (AuditLogTable.auditTableName eq tableName) and
                    (AuditLogTable.recordId eq recordId)
            }.single()
    }
}
