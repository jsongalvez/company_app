package com.companyb.companyapp.service
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.service.session.SessionConcernService
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcernServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val rateId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val systemConcernId = TestFixtures.uuid()
    private val promotedSessionId = TestFixtures.uuid()
    private val promotedClientId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "concern-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestClient(promotedClientId)
        IdentityFixtures.grantEditBranchData(callerId, sourceId)
        insertSessionBaseRate()
        createSession(callerId, sessionId)
        createSession(callerId, promotedSessionId, clientId = promotedClientId)
        insertSystemConcern()
    }

    @Test
    fun `list concerns returns all concerns including system-seeded`() {
        val concerns = ConcernService.listAll()

        assertTrue(concerns.isNotEmpty())
        val systemConcern = concerns.find { it.id == systemConcernId }
        assertNotNull(systemConcern)
        assertNull(systemConcern.createdBy)
        assertEquals("Knee Pain", systemConcern.label)
    }

    @Test
    fun `listAll without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "concern-other")

        val concerns = ConcernService.listAll()

        assertTrue(concerns.isNotEmpty())
    }

    @Test
    fun `add concern to session succeeds and writes audit`() {
        SessionConcernService.addToSession(callerId, sessionId, systemConcernId)

        val concerns = SessionConcernService.getForSession(callerId, sessionId)
        assertEquals(1, concerns.size)
        assertEquals(systemConcernId, concerns[0].id)
        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionConcernTable.tableName) and
                            (AuditLogTable.recordId eq sessionId)
                    }.count()
            }
        assertEquals(1L, auditCount)
    }

    @Test
    fun `add concern on REMITTED day with reason succeeds and flags audit entry`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        val remittedSessionId = TestFixtures.uuid()
        val remittedClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(remittedSessionId, remittedClientId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        SessionConcernService.addToSession(callerId, remittedSessionId, systemConcernId, "Coordinator correction")

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionConcernTable.tableName) and
                            (AuditLogTable.recordId eq remittedSessionId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `add concern to session is idempotent`() {
        SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
        SessionConcernService.addToSession(callerId, sessionId, systemConcernId)

        val concerns = SessionConcernService.getForSession(callerId, sessionId)
        assertEquals(1, concerns.size)
    }

    @Test
    fun `add concern without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "concern-other")

        SessionConcernService.addToSession(otherCaller, sessionId, systemConcernId)

        val concerns = SessionConcernService.getForSession(otherCaller, sessionId)
        assertEquals(1, concerns.size)
        assertEquals(systemConcernId, concerns[0].id)
    }

    @Test
    fun `add concern throws 404 for non-existent session`() {
        assertFailsWith<NotFoundException> {
            SessionConcernService.addToSession(callerId, TestFixtures.uuid(), systemConcernId)
        }
    }

    @Test
    fun `add concern throws 404 for non-existent concern`() {
        assertFailsWith<NotFoundException> {
            SessionConcernService.addToSession(callerId, sessionId, TestFixtures.uuid())
        }
    }

    @Test
    fun `remove concern from session succeeds and writes audit`() {
        SessionConcernService.addToSession(callerId, sessionId, systemConcernId)

        SessionConcernService.removeFromSession(callerId, sessionId, systemConcernId)

        val concerns = SessionConcernService.getForSession(callerId, sessionId)
        assertTrue(concerns.isEmpty())

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionConcernTable.tableName) and
                            (AuditLogTable.changedBy eq callerId)
                    }.count()
            }
        assertTrue(auditCount >= 1L)
    }

    @Test
    fun `remove concern without EDIT_BRANCH_DATA is allowed at service layer`() {
        SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "concern-other")

        SessionConcernService.removeFromSession(otherCaller, sessionId, systemConcernId)

        val concerns = SessionConcernService.getForSession(otherCaller, sessionId)
        assertTrue(concerns.isEmpty())
    }

    @Test
    fun `remove concern throws 404 for non-existent concern`() {
        assertFailsWith<NotFoundException> {
            SessionConcernService.removeFromSession(callerId, sessionId, TestFixtures.uuid())
        }
    }

    @Test
    fun `promote concern creates concern, links to session, and nullifies otherConcerns`() {
        SessionConcernService.addToSession(callerId, promotedSessionId, systemConcernId)

        val promoted =
            SessionConcernService.promoteConcern(
                callerId,
                promotedSessionId,
                TestFixtures.uuid(),
                "Back Pain",
            )

        assertNotNull(promoted)
        assertEquals("Back Pain", promoted.label)
        assertEquals(callerId, promoted.createdBy)

        val concerns = SessionConcernService.getForSession(callerId, promotedSessionId)
        assertTrue(concerns.any { it.id == promoted.id })

        val session = SessionRepository.findById(promotedSessionId)!!
        assertNull(session.otherConcerns)
    }

    @Test
    fun `promoted concern is discoverable in all concerns list`() {
        val promoted =
            SessionConcernService.promoteConcern(
                callerId,
                promotedSessionId,
                TestFixtures.uuid(),
                "Neck Pain",
            )

        val allConcerns = ConcernService.listAll()
        assertTrue(allConcerns.any { it.id == promoted.id })
        assertNotNull(allConcerns.find { it.id == promoted.id }?.createdBy)
    }

    @Test
    fun `getForSession on REMITTED day with EDIT_PAST_DAY returns concerns without requiring a reason`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        val remittedSessionId = TestFixtures.uuid()
        val remittedClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(remittedSessionId, remittedClientId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        SessionConcernService.addToSession(callerId, remittedSessionId, systemConcernId, "Coordinator correction")

        val concerns = SessionConcernService.getForSession(callerId, remittedSessionId)

        assertEquals(1, concerns.size)
        assertEquals(systemConcernId, concerns[0].id)
    }

    @Test
    fun `getForSession on REMITTED day without EDIT_PAST_DAY is forbidden`() {
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "concern-remitted-no-caps")
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        val remittedSessionId = TestFixtures.uuid()
        val remittedClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(remittedSessionId, remittedClientId, remittedDayId)

        assertFailsWith<ForbiddenException> {
            SessionConcernService.getForSession(otherCaller, remittedSessionId)
        }
    }

    @Test
    fun `getForSession without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "concern-other")

        val concerns = SessionConcernService.getForSession(otherCaller, promotedSessionId)

        assertTrue(concerns.isEmpty())
    }

    @Test
    fun `promote concern without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "concern-other")

        val promoted =
            SessionConcernService.promoteConcern(
                otherCaller,
                promotedSessionId,
                TestFixtures.uuid(),
                "Shoulder Pain",
            )

        assertNotNull(promoted)
        assertEquals("Shoulder Pain", promoted.label)
        assertEquals(otherCaller, promoted.createdBy)
    }

    @Test
    fun `promote concern writes audit log`() {
        val promoted =
            SessionConcernService.promoteConcern(
                callerId,
                promotedSessionId,
                TestFixtures.uuid(),
                "Elbow Pain",
            )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq ConcernTable.tableName) and
                            (AuditLogTable.changedBy eq callerId)
                    }.count()
            }
        assertTrue(auditCount >= 1L)
    }

    @Test
    fun `promote concern with duplicate UUID returns existing concern idempotently`() {
        val concernId = TestFixtures.uuid()
        val first = SessionConcernService.promoteConcern(callerId, promotedSessionId, concernId, "Headache")

        val second = SessionConcernService.promoteConcern(callerId, promotedSessionId, concernId, "Different Label")

        assertEquals("Headache", second.label)
        assertEquals(first.id, second.id)

        val allConcerns = ConcernService.listAll()
        assertEquals(1, allConcerns.count { it.id == concernId })
    }

    @Suppress("LongParameterList")
    private fun createSession(
        callerId: UUID,
        id: UUID,
        clientId: UUID = this.clientId,
        branchId: UUID = this.branchId,
        isWalkIn: Boolean = false,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
    ) = SessionService.create(
        callerId = callerId,
        id = id,
        clientId = clientId,
        branchId = branchId,
        isWalkIn = isWalkIn,
        requestedPractitionerId = null,
        finalPrice = finalPrice,
        remarks = "Test session",
        otherConcerns = null,
        nextAppointmentDate = null,
    )

    private fun insertSessionBaseRate(
        id: UUID = rateId,
        branchId: UUID = this.branchId,
        sessionType: SessionType = SessionType.REGULAR,
    ) {
        transaction {
            SessionBaseRateTable.insert {
                it[SessionBaseRateTable.id] = id
                it[SessionBaseRateTable.setBy] = callerId
                it[SessionBaseRateTable.branchId] = branchId
                it[SessionBaseRateTable.sessionType] = sessionType
                it[SessionBaseRateTable.rate] = BigDecimal("2500.00")
                it[SessionBaseRateTable.effectiveFrom] = TestFixtures.now.minusDays(1)
                it[SessionBaseRateTable.effectiveUntil] = TestFixtures.now.plusDays(365)
            }
        }
    }

    private fun insertSystemConcern() {
        transaction {
            ConcernTable.insert {
                it[ConcernTable.id] = systemConcernId
                it[ConcernTable.label] = "Knee Pain"
            }
        }
    }
}
