package com.companyb.companyapp.service.session
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * #348 — the two new session reads behind the SessionCreate flow: the pre-create preview
 * (predicted type + base rate, same inputs as create) and the practitioners list read (the
 * add-self refresh route).
 */
class SessionReadsPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val regularRateId = TestFixtures.uuid()
    private val secondSessionRateId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "session-reads-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        insertRate(regularRateId, SessionType.REGULAR, "2500.00")
        insertRate(secondSessionRateId, SessionType.SECOND_SESSION, "2000.00")
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, regularRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, secondSessionRateId)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        // Today's day row — the practitioners read gates on its readability.
        BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
    }

    @Test
    fun `preview returns REGULAR and base rate for a first-visit client`() {
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)

        val preview = SessionService.previewSession(branchId, clientId)

        assertEquals(SessionType.REGULAR, preview.sessionType)
        assertEquals(BigDecimal("2500.00"), preview.basePrice)
    }

    @Test
    fun `preview counts prior sessions into SECOND_SESSION with its own rate`() {
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        insertPriorSession(TestFixtures.uuid(), clientId, SessionType.REGULAR)

        val preview = SessionService.previewSession(branchId, clientId)

        assertEquals(SessionType.SECOND_SESSION, preview.sessionType)
        assertEquals(BigDecimal("2000.00"), preview.basePrice)
    }

    @Test
    fun `preview excludes voided and medical-mission sessions from history`() {
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        val voidedId = TestFixtures.uuid()
        insertPriorSession(voidedId, clientId, SessionType.REGULAR)
        transaction {
            SessionVoidTable.insert {
                it[sessionId] = voidedId
                it[voidedBy] = callerId
                it[voidReason] = "test"
            }
        }
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, voidedId)
        insertPriorSession(TestFixtures.uuid(), clientId, SessionType.MEDICAL_MISSION)

        val preview = SessionService.previewSession(branchId, clientId)

        assertEquals(SessionType.REGULAR, preview.sessionType)
    }

    @Test
    fun `preview 404s on unknown branch or client`() {
        assertFailsWith<NotFoundException> { SessionService.previewSession(TestFixtures.uuid(), TestFixtures.uuid()) }

        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        assertFailsWith<NotFoundException> { SessionService.previewSession(branchId, TestFixtures.uuid()) }
    }

    @Test
    fun `preview 400s when no base rate is configured for the predicted type`() {
        // SUBSEQUENT has no rate row at this branch; two priors make it the predicted type.
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        insertPriorSession(TestFixtures.uuid(), clientId, SessionType.REGULAR)
        insertPriorSession(TestFixtures.uuid(), clientId, SessionType.SECOND_SESSION)

        assertFailsWith<ValidationException> { SessionService.previewSession(branchId, clientId) }
    }

    // --- #424 — free (₱0) last session defaults the next visit's price to SUBSEQUENT ---

    @Test
    fun `free last session defaults preview price to SUBSEQUENT base rate`() {
        val subsequentRateId = TestFixtures.uuid()
        insertRate(subsequentRateId, SessionType.SUBSEQUENT, "1500.00")
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, subsequentRateId)
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        insertPriorSession(
            TestFixtures.uuid(),
            clientId,
            SessionType.REGULAR,
            finalPrice = BigDecimal.ZERO,
            createdAt = TestFixtures.now.minusDays(1),
        )

        // One prior → SECOND_SESSION type; the ₱0 trigger swaps only the default price.
        val preview = SessionService.previewSession(branchId, clientId)

        assertEquals(SessionType.SECOND_SESSION, preview.sessionType)
        assertEquals(BigDecimal("1500.00"), preview.basePrice)
    }

    @Test
    fun `mission session as most recent visit never triggers the SUBSEQUENT default`() {
        val subsequentRateId = TestFixtures.uuid()
        insertRate(subsequentRateId, SessionType.SUBSEQUENT, "1500.00")
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, subsequentRateId)
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        insertPriorSession(
            TestFixtures.uuid(),
            clientId,
            SessionType.REGULAR,
            createdAt = TestFixtures.now.minusDays(2),
        )
        // Mission visits are ₱0 by definition — they must not read as a free session here.
        insertPriorSession(
            TestFixtures.uuid(),
            clientId,
            SessionType.MEDICAL_MISSION,
            createdAt = TestFixtures.now.minusDays(1),
        )

        val preview = SessionService.previewSession(branchId, clientId)

        assertEquals(SessionType.SECOND_SESSION, preview.sessionType)
        assertEquals(BigDecimal("2000.00"), preview.basePrice)
    }

    @Test
    fun `voided free session does not trigger the SUBSEQUENT default`() {
        val subsequentRateId = TestFixtures.uuid()
        insertRate(subsequentRateId, SessionType.SUBSEQUENT, "1500.00")
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, subsequentRateId)
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        insertPriorSession(
            TestFixtures.uuid(),
            clientId,
            SessionType.REGULAR,
            createdAt = TestFixtures.now.minusDays(2),
        )
        val voidedFreeId = TestFixtures.uuid()
        insertPriorSession(
            voidedFreeId,
            clientId,
            SessionType.REGULAR,
            finalPrice = BigDecimal.ZERO,
            createdAt = TestFixtures.now.minusDays(1),
        )
        transaction {
            SessionVoidTable.insert {
                it[sessionId] = voidedFreeId
                it[voidedBy] = callerId
                it[voidReason] = "test"
            }
        }
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, voidedFreeId)

        // Most recent non-voided session is the paid one — derived rate stands.
        val preview = SessionService.previewSession(branchId, clientId)

        assertEquals(SessionType.SECOND_SESSION, preview.sessionType)
        assertEquals(BigDecimal("2000.00"), preview.basePrice)
    }

    @Test
    fun `create after free session persists SUBSEQUENT base price while override wins`() {
        val subsequentRateId = TestFixtures.uuid()
        insertRate(subsequentRateId, SessionType.SUBSEQUENT, "1500.00")
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, subsequentRateId)
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        insertPriorSession(
            TestFixtures.uuid(),
            clientId,
            SessionType.REGULAR,
            finalPrice = BigDecimal.ZERO,
            createdAt = TestFixtures.now.minusDays(1),
        )
        val newSessionId = TestFixtures.uuid()

        val result =
            SessionService.create(
                callerId = callerId,
                id = newSessionId,
                clientId = clientId,
                branchId = branchId,
                isWalkIn = false,
                requestedPractitionerId = null,
                finalPrice = BigDecimal("1234.56"),
                remarks = null,
                otherConcerns = null,
                bookedAt = null,
                nextAppointmentDate = null,
            )
        trackOwned(SessionTable, SessionTable.id, newSessionId)

        assertEquals(SessionType.SECOND_SESSION, result.session.sessionType)
        // Default offered base is SUBSEQUENT; the practitioner's explicit price wins.
        assertEquals(BigDecimal("1500.00"), result.session.basePrice)
        assertEquals(BigDecimal("1234.56"), result.session.finalPrice)
    }

    @Test
    fun `practitioners read returns rows ordered by slot`() {
        val sessionId = TestFixtures.uuid()
        val dayId = BranchDayService.resolveOrCreate(branchId, TestFixtures.today).id
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        DatabaseTestHelper.insertTestSession(sessionId, clientId, dayId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        val late = TestFixtures.uuid()
        val early = TestFixtures.uuid()
        val latePractitioner = TestFixtures.uuid()
        val earlyPractitioner = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(latePractitioner, "session-reads-late")
        trackOwned(AppUserTable, AppUserTable.id, latePractitioner)
        DatabaseTestHelper.insertTestUser(earlyPractitioner, "session-reads-early")
        trackOwned(AppUserTable, AppUserTable.id, earlyPractitioner)
        transaction {
            SessionPractitionerRepository.addInTransaction(
                id = late,
                sessionId = sessionId,
                practitionerId = latePractitioner,
                slotAtTime = 7,
                remarks = null,
            )
            SessionPractitionerRepository.addInTransaction(
                id = early,
                sessionId = sessionId,
                practitionerId = earlyPractitioner,
                slotAtTime = 2,
                remarks = null,
            )
        }
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        val practitioners = SessionService.getSessionPractitioners(callerId, sessionId)

        assertEquals(listOf(early, late), practitioners.map { it.id })
        assertEquals(listOf(2, 7), practitioners.map { it.slotAtTime.toInt() })
    }

    @Test
    fun `practitioners read 404s for a missing session`() {
        assertFailsWith<NotFoundException> { SessionService.getSessionPractitioners(callerId, TestFixtures.uuid()) }
    }

    private fun insertRate(
        id: UUID,
        sessionType: SessionType,
        rate: String,
    ) {
        transaction {
            SessionBaseRateTable.insert {
                it[SessionBaseRateTable.id] = id
                it[SessionBaseRateTable.setBy] = callerId
                it[SessionBaseRateTable.branchId] = this@SessionReadsPostgresTest.branchId
                it[SessionBaseRateTable.sessionType] = sessionType
                it[SessionBaseRateTable.rate] = BigDecimal(rate)
                it[SessionBaseRateTable.effectiveFrom] = TestFixtures.now.minusDays(1)
                it[SessionBaseRateTable.effectiveUntil] = TestFixtures.now.plusDays(365)
            }
        }
    }

    /**
     * History rows are COMPLETED — a second PENDING row per client would hit the partial unique index.
     * [createdAt] pins recency explicitly (#424): two same-day inserts otherwise race on the
     * DB clock and make "most recent" nondeterministic.
     */
    private fun insertPriorSession(
        id: UUID,
        clientId: UUID,
        sessionType: SessionType,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
        createdAt: OffsetDateTime? = null,
    ) {
        val dayId =
            transaction {
                BranchDayService.resolveOrCreate(branchId, TestFixtures.today).id
            }
        DatabaseTestHelper.insertTestSession(
            id,
            clientId,
            dayId,
            sessionType = sessionType,
            sessionStatus = SessionStatus.COMPLETED,
            finalPrice = finalPrice,
        )
        trackOwned(SessionTable, SessionTable.id, id)
        if (createdAt != null) {
            transaction {
                SessionTable.update({ SessionTable.id eq id }) {
                    it[SessionTable.createdAt] = createdAt
                }
            }
        }
    }
}
