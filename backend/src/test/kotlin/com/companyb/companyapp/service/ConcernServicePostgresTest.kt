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
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
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
    private val callerId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val rateId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val systemConcernId = UUID.randomUUID()
    private val promotedSessionId = UUID.randomUUID()
    private val promotedClientId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "concern-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        DatabaseTestHelper.insertTestClient(promotedClientId)
        trackOwned(ClientTable, ClientTable.id, promotedClientId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        insertSessionBaseRate()
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        createSession(callerId, promotedSessionId, clientId = promotedClientId)
        trackOwned(SessionTable, SessionTable.id, promotedSessionId)
        insertSystemConcern()
        trackOwned(ConcernTable, ConcernTable.id, systemConcernId)
        trackOwned(SessionConcernTable, SessionConcernTable.sessionId, sessionId)
        trackOwned(SessionConcernTable, SessionConcernTable.sessionId, promotedSessionId)
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
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)

        val concerns = ConcernService.listAll()

        assertTrue(concerns.isNotEmpty())
    }

    @Test
    fun `add concern to session succeeds and writes audit`() {
        ConcernService.addToSession(callerId, sessionId, systemConcernId)

        val concerns = ConcernService.getForSession(sessionId)
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

        val concerns = ConcernService.getForSession(sessionId)
        assertEquals(1, concerns.size)
    }

    @Test
    fun `add concern without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        ConcernService.addToSession(otherCaller, sessionId, systemConcernId)

        val concerns = ConcernService.getForSession(sessionId)
        assertEquals(1, concerns.size)
        assertEquals(systemConcernId, concerns[0].id)
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

        val concerns = ConcernService.getForSession(sessionId)
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
        ConcernService.addToSession(callerId, sessionId, systemConcernId)
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        ConcernService.removeFromSession(otherCaller, sessionId, systemConcernId)

        val concerns = ConcernService.getForSession(sessionId)
        assertTrue(concerns.isEmpty())
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

        val promoted = ConcernService.promoteConcern(callerId, UUID.randomUUID(), promotedSessionId, "Back Pain")
        trackOwned(ConcernTable, ConcernTable.id, promoted.id)

        assertNotNull(promoted)
        assertEquals("Back Pain", promoted.label)
        assertEquals(callerId, promoted.createdBy)

        val concerns = ConcernService.getForSession(promotedSessionId)
        assertTrue(concerns.any { it.id == promoted.id })

        val session = SessionRepository.findById(promotedSessionId)!!
        assertNull(session.otherConcerns)
    }

    @Test
    fun `promoted concern is discoverable in all concerns list`() {
        val promoted = ConcernService.promoteConcern(callerId, UUID.randomUUID(), promotedSessionId, "Neck Pain")
        trackOwned(ConcernTable, ConcernTable.id, promoted.id)

        val allConcerns = ConcernService.listAll()
        assertTrue(allConcerns.any { it.id == promoted.id })
        assertNotNull(allConcerns.find { it.id == promoted.id }?.createdBy)
    }

    @Test
    fun `getForSession without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)

        val concerns = ConcernService.getForSession(promotedSessionId)

        assertTrue(concerns.isEmpty())
    }

    @Test
    fun `promote concern without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "concern-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        val promoted = ConcernService.promoteConcern(otherCaller, UUID.randomUUID(), promotedSessionId, "Shoulder Pain")
        trackOwned(ConcernTable, ConcernTable.id, promoted.id)

        assertNotNull(promoted)
        assertEquals("Shoulder Pain", promoted.label)
        assertEquals(otherCaller, promoted.createdBy)
    }

    @Test
    fun `promote concern writes audit log`() {
        val promoted = ConcernService.promoteConcern(callerId, UUID.randomUUID(), promotedSessionId, "Elbow Pain")
        trackOwned(ConcernTable, ConcernTable.id, promoted.id)

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
        val concernId = UUID.randomUUID()
        val first = ConcernService.promoteConcern(callerId, concernId, promotedSessionId, "Headache")
        trackOwned(ConcernTable, ConcernTable.id, first.id)

        val second = ConcernService.promoteConcern(callerId, concernId, promotedSessionId, "Different Label")

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
}
