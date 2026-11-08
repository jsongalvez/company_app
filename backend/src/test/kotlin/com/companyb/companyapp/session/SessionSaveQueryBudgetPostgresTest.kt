package com.companyb.companyapp.session

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Saving a session is one transaction with each fact read once: same answers, few queries. */
class SessionSaveQueryBudgetPostgresTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val staff = TestFixtures.uuid()
    private val notClockedIn = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        listOf(staff, notClockedIn).forEachIndexed { i, id -> IdentityFixtures.insertTestUser(id, "save-$i") }
        BranchWorkforceFixtures.insertTestBranch(branchId, "Save Branch $branchId")
        listOf(staff, notClockedIn).forEach {
            IdentityFixtures.grantCapability(
                userId = it,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH,
                contextId = branchId,
                sourceId = sourceId,
            )
        }
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, staff)
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, staff, SessionType.SECOND_SESSION)
        SessionClientFixtures.insertTestClient(clientId)
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, staff)
    }

    @Test
    fun `a save writes the session and answers in a handful of queries`() {
        val id = TestFixtures.uuid()
        val (saved, queries) = countQueries { save(staff, id) }

        assertTrue(saved.result.created)
        assertEquals(id, saved.result.session.id)
        assertEquals(SessionType.REGULAR, saved.result.session.sessionType)
        assertEquals(
            "2500.00",
            saved.result.session.basePrice
                .toPlainString(),
        )
        assertEquals(1, auditRows(id))
        assertTrue(queries <= QUERY_BUDGET, "Saving a session ran $queries queries, budget $QUERY_BUDGET")
    }

    @Test
    fun `saving the same session again changes nothing and is not created`() {
        val id = TestFixtures.uuid()
        save(staff, id)
        val again = save(staff, id)

        assertEquals(false, again.result.created)
        assertEquals(1, auditRows(id))
    }

    @Test
    fun `a second visit by the same client is priced as a second session`() {
        val client = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(
            TestFixtures.uuid(),
            client,
            BranchWorkforceFixtures.createBranchDayForToday(branchId),
            sessionStatus = SessionStatus.COMPLETED,
        )
        val second = save(staff, TestFixtures.uuid(), client)

        assertEquals(SessionType.SECOND_SESSION, second.result.session.sessionType)
    }

    @Test
    fun `someone who has not clocked in is refused and nothing is written`() {
        val id = TestFixtures.uuid()
        assertFailsWith<ForbiddenException> { save(notClockedIn, id) }

        assertNull(SessionRepository.findById(id))
        assertEquals(0, auditRows(id))
    }

    @Test
    fun `someone with no right at the branch is refused`() {
        val stranger = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(stranger, "save-stranger")
        assertFailsWith<ForbiddenException> { save(stranger, TestFixtures.uuid()) }
    }

    @Test
    fun `an unknown branch or client is not found and an id used by someone else conflicts`() {
        assertFailsWith<NotFoundException> { save(staff, TestFixtures.uuid(), branch = TestFixtures.uuid()) }
        assertFailsWith<NotFoundException> { save(staff, TestFixtures.uuid(), client = TestFixtures.uuid()) }

        val id = TestFixtures.uuid()
        save(staff, id)
        assertFailsWith<ConflictException> { save(staff, id, client = SessionClientFixtures.insertTestClient()) }
    }

    private fun save(
        caller: UUID,
        id: UUID,
        client: UUID = clientId,
        branch: UUID = branchId,
    ): SavedSession =
        SessionSaveService.save(
            caller,
            NewSession(
                id = id,
                clientId = client,
                branchId = branch,
                isWalkIn = false,
                requestedPractitionerId = null,
                finalPrice = BigDecimal("2500.00"),
                remarks = null,
                otherConcerns = null,
                nextAppointmentDate = null,
                reason = null,
            ),
        )

    private fun auditRows(sessionId: UUID): Int =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionTable.tableName) and
                        (AuditLogTable.recordId eq sessionId)
                }.count()
                .toInt()
        }

    private fun <T> countQueries(block: () -> T): Pair<T, Int> {
        val source = JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource())
        Database.connect(source)
        try {
            RequestQueryTally.startRequest()
            val answer = block()
            return answer to RequestQueryTally.snapshot().queries
        } finally {
            RequestQueryTally.endRequest()
            Database.connect(TestDatabaseLifecycle.requireTestDataSource())
        }
    }

    private companion object {
        const val QUERY_BUDGET = 15
    }
}
