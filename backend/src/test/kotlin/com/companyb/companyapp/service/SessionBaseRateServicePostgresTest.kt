package com.companyb.companyapp.service
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.SessionBaseRateRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionBaseRateServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val rateId = TestFixtures.uuid()
    private val rateId2 = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "rate-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, name = "Rate-Clinic-$branchId")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestBranch(otherBranchId, name = "Rate-Clinic-$otherBranchId")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `set rate for branch creates rate and writes audit`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val result =
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)

        assertTrue(result.created)
        assertEquals(SessionType.REGULAR, result.rate.sessionType)
        assertEquals("2500.00", result.rate.rate.toPlainString())
        assertEquals(branchId, result.rate.branchId)
        assertEquals(1L, auditEntryCount(rateId))
    }

    @Test
    fun `set rate without MANAGE_PRODUCTS is allowed at service layer`() {
        val newRateId = TestFixtures.uuid()
        val result =
            SessionService.setRate(
                callerId,
                newRateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, newRateId)

        assertTrue(result.created)
        assertEquals(SessionType.REGULAR, result.rate.sessionType)
        assertEquals("2500.00", result.rate.rate.toPlainString())
        assertEquals(branchId, result.rate.branchId)
    }

    @Test
    fun `setting second rate for same branch and type deactivates first`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val first =
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        val second =
            SessionService.setRate(
                callerId,
                rateId2,
                branchId,
                SessionType.REGULAR,
                BigDecimal("3000.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId2)

        assertTrue(first.created)
        assertTrue(second.created)

        val persistedRates =
            transaction {
                SessionBaseRateTable
                    .selectAll()
                    .where { SessionBaseRateTable.id eq rateId }
                    .single()[SessionBaseRateTable.effectiveUntil] to
                    SessionBaseRateTable
                        .selectAll()
                        .where { SessionBaseRateTable.id eq rateId2 }
                        .single()[SessionBaseRateTable.effectiveFrom]
            }
        assertEquals(persistedRates.first, persistedRates.second)

        val activeRates = SessionService.findActiveRates(branchId)
        assertEquals(1, activeRates.size)
        assertEquals("3000.00", activeRates[0].rate.toPlainString())
    }

    @Test
    fun `find active rates returns only current rates`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        SessionService.setRate(callerId, rateId, branchId, SessionType.REGULAR, BigDecimal("2500.00"))
        SessionService.setRate(callerId, rateId2, branchId, SessionType.SECOND_SESSION, BigDecimal("2000.00"))
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId2)

        val activeRates = SessionService.findActiveRates(branchId)

        assertEquals(2, activeRates.size)
    }

    @Test
    fun `duplicate idempotent set rate returns existing row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val first =
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        val duplicate =
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("2500.00", duplicate.rate.rate.toPlainString())
        assertEquals(1L, auditEntryCount(rateId))
    }

    @Test
    fun `duplicate UUID from another branch is rejected without changing active rate`() {
        val first =
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        val before = SessionService.findActiveRates(branchId).single()

        assertFailsWith<NotFoundException> {
            SessionService.setRate(
                callerId,
                rateId,
                otherBranchId,
                SessionType.REGULAR,
                BigDecimal("3000.00"),
            )
        }

        val after = SessionService.findActiveRates(branchId).single()
        assertEquals(first.rate.effectiveUntil, after.effectiveUntil)
        assertEquals(before.rate, after.rate)
        assertEquals(1L, auditEntryCount(rateId))
    }

    @Test
    fun `duplicate UUID with altered request is rejected without changing active rate`() {
        SessionService.setRate(
            callerId,
            rateId,
            branchId,
            SessionType.REGULAR,
            BigDecimal("2500.00"),
        )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        val before = SessionService.findActiveRates(branchId).single()

        assertFailsWith<ConflictException> {
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("3000.00"),
            )
        }

        val after = SessionService.findActiveRates(branchId).single()
        assertEquals(before.rate, after.rate)
        assertEquals(before.effectiveUntil, after.effectiveUntil)
        assertEquals(1L, auditEntryCount(rateId))
    }

    @Test
    fun `same UUID retry remains idempotent after rate replacement`() {
        val first =
            SessionService.setRate(
                callerId,
                rateId,
                branchId,
                SessionType.REGULAR,
                BigDecimal("2500.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        SessionService.setRate(
            callerId,
            rateId2,
            branchId,
            SessionType.REGULAR,
            BigDecimal("3000.00"),
        )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId2)
        assertEquals(2L, auditEntryCount(rateId))

        val retry = SessionService.setRate(callerId, rateId, branchId, SessionType.REGULAR, BigDecimal("2500.00"))

        assertFalse(retry.created)
        assertEquals(first.rate.id, retry.rate.id)
        assertEquals(2L, auditEntryCount(rateId))
    }

    @Test
    fun `concurrent distinct rates leave one active rate`() {
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Throwable?>())
        val ids = listOf(TestFixtures.uuid(), TestFixtures.uuid())
        val threads =
            ids.map { id ->
                thread(start = false) {
                    start.await()
                    try {
                        SessionService.setRate(
                            callerId,
                            id,
                            branchId,
                            SessionType.REGULAR,
                            BigDecimal("2500.00"),
                        )
                        results += null
                    } catch (error: Throwable) {
                        results += error
                    }
                }
            }
        threads.forEach { it.start() }
        start.countDown()

        // Join is bounded so a database lock regression cannot hang the test suite.
        threads.forEach { it.join(CONCURRENT_JOIN_MILLIS) }

        ids.forEach { trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, it) }
        assertEquals(2, results.size)
        assertEquals(2, results.count { it == null })
        assertEquals(0, results.count { it is ConflictException })
        assertEquals(1, SessionService.findActiveRates(branchId).size)
    }

    @Test
    fun `branch creation provisions the five documented default rates`() {
        val seededBranchId = TestFixtures.uuid()
        val created = BranchService.create(callerId, seededBranchId, "Seeded-Clinic-$seededBranchId", BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, seededBranchId)
        trackChildRowsOfParent(SessionBaseRateTable, SessionBaseRateTable.branchId, seededBranchId)

        assertTrue(created.created)
        val rates = SessionService.findActiveRates(seededBranchId).associateBy { it.sessionType }
        assertEquals(5, rates.size)
        assertEquals("2500.00", rates.getValue(SessionType.REGULAR).rate.toPlainString())
        assertEquals("2000.00", rates.getValue(SessionType.SECOND_SESSION).rate.toPlainString())
        assertEquals("1500.00", rates.getValue(SessionType.SUBSEQUENT).rate.toPlainString())
        assertEquals("3500.00", rates.getValue(SessionType.PROVINCIAL_FIRST).rate.toPlainString())
        assertEquals("0.00", rates.getValue(SessionType.MEDICAL_MISSION).rate.toPlainString())
        assertEquals(callerId, rates.getValue(SessionType.REGULAR).setBy)

        // Idempotent retry: created=false seeds nothing new and rotates nothing.
        BranchService.create(callerId, seededBranchId, "Seeded-Clinic-$seededBranchId", BranchType.CLINIC)
        assertEquals(5, SessionService.findActiveRates(seededBranchId).size)

        // A fresh branch can create its first session without failing — the acceptance root.
        val clientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, clientId)
        val sessionId = TestFixtures.uuid()
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = seededBranchId,
            isWalkIn = true,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            bookedAt = null,
            nextAppointmentDate = null,
        )
        trackOwned(SessionTable, SessionTable.id, sessionId)
        // Lazy day bootstrap inside create — tracked by id so DELETION_ORDER removes it after
        // the session (a child-match deletion would run too early).
        val seededDayId =
            transaction {
                BranchDayTable
                    .selectAll()
                    .where { BranchDayTable.branchId eq seededBranchId }
                    .single()[BranchDayTable.id]
            }
        trackOwned(BranchDayTable, BranchDayTable.id, seededDayId)
    }

    @Test
    fun `setting a MEDICAL_MISSION rate normalizes any caller price to zero`() {
        val missionRateId = TestFixtures.uuid()
        val result =
            SessionService.setRate(
                callerId,
                missionRateId,
                branchId,
                SessionType.MEDICAL_MISSION,
                BigDecimal("500.00"),
            )
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, missionRateId)

        assertTrue(result.created)
        assertEquals("0.00", result.rate.rate.toPlainString())
        // Retry with the same (un-normalized) caller price stays idempotent after normalization.
        val retry =
            SessionService.setRate(callerId, missionRateId, branchId, SessionType.MEDICAL_MISSION, BigDecimal("500.00"))
        assertFalse(retry.created)
        assertEquals("0.00", retry.rate.rate.toPlainString())
    }

    @Test
    fun `find rates for branch with no rates returns empty`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val rates = SessionService.findActiveRates(branchId)
        assertTrue(rates.isEmpty())
    }

    @Test
    fun `findActiveRates without MANAGE_PRODUCTS is allowed at service layer`() {
        val rates = SessionService.findActiveRates(branchId)

        assertTrue(rates.isEmpty())
    }

    private fun auditEntryCount(rateId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionBaseRateTable.tableName) and
                        (AuditLogTable.recordId eq rateId)
                }.count()
        }

    private companion object {
        const val CONCURRENT_JOIN_MILLIS = 30_000L
    }
}
