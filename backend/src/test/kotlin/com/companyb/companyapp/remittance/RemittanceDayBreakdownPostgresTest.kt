package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.remittance.RemittanceDayBreakdown
import com.companyb.companyapp.remittance.RemittanceDayBreakdownTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemittanceDayBreakdownPostgresTest : RemittanceLineServicePostgresBase() {
    @Test
    fun `add day breakdown succeeds`() {
        val remittance = createDraftRemittance()

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )

        assertNotNull(breakdown)
        assertEquals(remittance.id, breakdown.remittanceId)
        assertEquals(branchDayId, breakdown.branchDayId)
    }

    @Test
    fun `add day breakdown duplicate returns existing`() {
        val remittance = createDraftRemittance()
        val breakdownId = TestFixtures.uuid()

        val first =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = breakdownId,
                branchDayId = branchDayId,
            )

        val second =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = breakdownId,
                branchDayId = branchDayId,
            )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `add day breakdown concurrent distinct IDs return existing`() {
        val remittance = createDraftRemittance()
        val executor = Executors.newFixedThreadPool(CONCURRENT_BREAKDOWNS)
        val ready = CountDownLatch(CONCURRENT_BREAKDOWNS)
        val start = CountDownLatch(1)
        val breakdownIds = (1..CONCURRENT_BREAKDOWNS).map { TestFixtures.uuid() }
        val futures =
            breakdownIds.map { breakdownId ->
                executor.submit<RemittanceDayBreakdown> {
                    ready.countDown()
                    start.await()
                    RemittanceService.addDayBreakdown(callerId, remittance.id, breakdownId, branchDayId)
                }
            }

        val results =
            try {
                assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
                start.countDown()
                futures.map { it.get() }
            } finally {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            }

        assertEquals(1, results.map { it.id }.toSet().size)
        assertEquals(1, remittanceDayBreakdownAuditCount())
        assertEquals(1, RemittanceService.getRemittance(remittance.id).dayBreakdowns.size)
    }

    @Test
    fun `add day breakdown rejects client ID already used by another remittance`() {
        val firstRemittance = createDraftRemittance()
        val secondRemittance = createDraftRemittance(RemittanceType.PRODUCT)
        val breakdownId = TestFixtures.uuid()

        RemittanceService.addDayBreakdown(callerId, firstRemittance.id, breakdownId, branchDayId)

        assertFailsWith<ConflictException> {
            RemittanceService.addDayBreakdown(callerId, secondRemittance.id, breakdownId, branchDayId)
        }
        assertTrue(RemittanceService.getRemittance(secondRemittance.id).dayBreakdowns.isEmpty())
        assertEquals(1, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `add day breakdown rejects same ID for another Branch Day`() {
        val remittance = createDraftRemittance()
        val otherDayId = BranchDayService.resolveOrCreate(branchId, TestFixtures.today.minusDays(1)).id
        val breakdownId = TestFixtures.uuid()

        RemittanceService.addDayBreakdown(callerId, remittance.id, breakdownId, branchDayId)

        assertFailsWith<ConflictException> {
            RemittanceService.addDayBreakdown(callerId, remittance.id, breakdownId, otherDayId)
        }
        assertEquals(1, RemittanceService.getRemittance(remittance.id).dayBreakdowns.size)
        assertEquals(1, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `add day breakdown rejects foreign branch day`() {
        val foreignBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(foreignBranchId)
        val foreignDay = BranchDayService.resolveOrCreate(foreignBranchId, TestFixtures.today)
        val remittance = createDraftRemittance()
        assertFailsWith<NotFoundException> {
            RemittanceService.addDayBreakdown(callerId, remittance.id, TestFixtures.uuid(), foreignDay.id)
        }
        assertTrue(RemittanceService.getRemittance(remittance.id).dayBreakdowns.isEmpty())
        assertEquals(0, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `add day breakdown rejects day outside remittance range`() {
        val remittance = createDraftRemittance()
        val outOfRangeDay = BranchDayService.resolveOrCreate(branchId, LocalDate.of(2026, 6, 1))

        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addDayBreakdown(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    id = TestFixtures.uuid(),
                    branchDayId = outOfRangeDay.id,
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))
        assertTrue(RemittanceService.getRemittance(remittance.id).dayBreakdowns.isEmpty())
        assertEquals(0, remittanceDayBreakdownAuditCount())
    }

    @Test
    fun `update header rejects range excluding existing line and day`() {
        val remittance = createDraftRemittance()
        createSession()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(),
        )
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittance.id,
            id = TestFixtures.uuid(),
            branchDayId = branchDayId,
        )
        val versionBefore = RemittanceService.getRemittance(remittance.id).remittance.version

        // Both fixtures sit on the operational today; a range strictly before today orphans them.
        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.updateHeader(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    RemittanceHeader(
                        type = RemittanceType.SESSION,
                        method = RemittanceMethod.BANK_TRANSFER,
                        dateRangeStart = TestFixtures.today.minusDays(60),
                        dateRangeEnd = TestFixtures.today.minusDays(30),
                    ),
                    expectedVersion = versionBefore,
                )
            }
        assertTrue(checkNotNull(error.message).contains("outside remittance range"))
        assertEquals(versionBefore, RemittanceService.getRemittance(remittance.id).remittance.version)
    }

    @Test
    fun `add day breakdown without capability is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = otherUser,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )

        assertNotNull(breakdown)
        assertEquals(remittance.id, breakdown.remittanceId)
    }

    @Test
    fun `add day breakdown to non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = TestFixtures.uuid(),
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `add day breakdown to non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        markRemittanceSubmitted(remittance.id)

        assertFailsWith<ValidationException> {
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
            )
        }
    }

    private fun remittanceDayBreakdownAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.auditTableName eq RemittanceDayBreakdownTable.tableName }
                .count()
        }
}
