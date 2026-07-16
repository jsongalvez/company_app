package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcernServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val rateId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val systemConcernId = UUID.randomUUID()
    private val promotedSessionId = UUID.randomUUID()
    private val promotedClientId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "concern-caller")
        DatabaseTestHelper.insertTestBranch(branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestClient(promotedClientId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        insertSessionBaseRate()
        createSession(callerId, sessionId)
        createSession(callerId, promotedSessionId, clientId = promotedClientId)
        insertSystemConcern()
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `list concerns returns all concerns including system-seeded`() {
        val concerns = ConcernService.listAll(callerId)

        assertTrue(concerns.isNotEmpty())
        val systemConcern = concerns.find { it.id == systemConcernId }
        assertNotNull(systemConcern)
        assertNull(systemConcern.createdBy)
        assertEquals("Knee Pain", systemConcern.label)
    }

    @Test
    fun `listAll without EDIT_BRANCH_DATA is forbidden`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")

        assertFailsWith<ForbiddenResponse> {
            ConcernService.listAll(otherCaller)
        }
    }

    @Test
    fun `add concern to session succeeds and writes audit`() {
        ConcernService.addToSession(callerId, sessionId, systemConcernId)

        val concerns = ConcernService.getForSession(callerId, sessionId)
        assertEquals(1, concerns.size)
        assertEquals(systemConcernId, concerns[0].id)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq SessionConcernTable.tableName }
                    .count()
            }
        assertEquals(1L, auditCount)
    }

    @Test
    fun `add concern to session is idempotent`() {
        ConcernService.addToSession(callerId, sessionId, systemConcernId)
        ConcernService.addToSession(callerId, sessionId, systemConcernId)

        val concerns = ConcernService.getForSession(callerId, sessionId)
        assertEquals(1, concerns.size)
    }

    @Test
    fun `add concern requires EDIT_BRANCH_DATA`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")

        assertFailsWith<ForbiddenResponse> {
            ConcernService.addToSession(otherCaller, sessionId, systemConcernId)
        }
    }

    @Test
    fun `add concern throws 404 for non-existent session`() {
        assertFailsWith<NotFoundResponse> {
            ConcernService.addToSession(callerId, UUID.randomUUID(), systemConcernId)
        }
    }

    @Test
    fun `add concern throws 404 for non-existent concern`() {
        assertFailsWith<NotFoundResponse> {
            ConcernService.addToSession(callerId, sessionId, UUID.randomUUID())
        }
    }

    @Test
    fun `remove concern from session succeeds and writes audit`() {
        ConcernService.addToSession(callerId, sessionId, systemConcernId)

        ConcernService.removeFromSession(callerId, sessionId, systemConcernId)

        val concerns = ConcernService.getForSession(callerId, sessionId)
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
    fun `remove concern requires EDIT_BRANCH_DATA`() {
        ConcernService.addToSession(callerId, sessionId, systemConcernId)
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")

        assertFailsWith<ForbiddenResponse> {
            ConcernService.removeFromSession(otherCaller, sessionId, systemConcernId)
        }
    }

    @Test
    fun `remove concern throws 404 for non-existent concern`() {
        assertFailsWith<NotFoundResponse> {
            ConcernService.removeFromSession(callerId, sessionId, UUID.randomUUID())
        }
    }

    @Test
    fun `promote concern creates concern, links to session, and nullifies otherConcerns`() {
        ConcernService.addToSession(callerId, promotedSessionId, systemConcernId)

        val promoted = ConcernService.promoteConcern(callerId, promotedSessionId, "Back Pain")

        assertNotNull(promoted)
        assertEquals("Back Pain", promoted.label)
        assertEquals(callerId, promoted.createdBy)

        val concerns = ConcernService.getForSession(callerId, promotedSessionId)
        assertTrue(concerns.any { it.id == promoted.id })

        val session = SessionRepository.findById(promotedSessionId)!!
        assertNull(session.otherConcerns)
    }

    @Test
    fun `promoted concern is discoverable in all concerns list`() {
        val promoted = ConcernService.promoteConcern(callerId, promotedSessionId, "Neck Pain")

        val allConcerns = ConcernService.listAll(callerId)
        assertTrue(allConcerns.any { it.id == promoted.id })
        assertNotNull(allConcerns.find { it.id == promoted.id }?.createdBy)
    }

    @Test
    fun `getForSession without EDIT_BRANCH_DATA is forbidden`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")

        assertFailsWith<ForbiddenResponse> {
            ConcernService.getForSession(otherCaller, promotedSessionId)
        }
    }

    @Test
    fun `promote concern requires EDIT_BRANCH_DATA`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")

        assertFailsWith<ForbiddenResponse> {
            ConcernService.promoteConcern(otherCaller, promotedSessionId, "Shoulder Pain")
        }
    }

    @Test
    fun `promote concern writes audit log`() {
        ConcernService.promoteConcern(callerId, promotedSessionId, "Elbow Pain")

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
        bookedAt = null,
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
                it[SessionBaseRateTable.effectiveFrom] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
                it[SessionBaseRateTable.effectiveUntil] = OffsetDateTime.now(ZoneOffset.UTC).plusDays(365)
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

    private fun deleteTestRows() {
        transaction {
            SessionConcernTable.deleteAll()
            SessionTable.deleteAll()
            SessionBaseRateTable.deleteAll()
            ClientTable.deleteAll()
            BranchDayTable.deleteAll()
            BranchTable.deleteAll()
            ConcernTable.deleteAll()
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or
                    (AuditLogTable.recordId eq sessionId) or
                    (AuditLogTable.recordId eq promotedSessionId) or
                    (AuditLogTable.recordId eq systemConcernId)
            }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
    }
}
