package com.companyb.companyapp.integration

import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.CompensationService
import com.companyb.companyapp.finance.ExpenseService
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshot
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshotRepository
import com.companyb.companyapp.remittance.RemittanceRepository
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import java.math.BigDecimal
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #517 — submit must hold the covered days before the aggregate reads, so a day-gated
 * write cannot commit between the sums and the REMITTED transition.
 *
 * Each iteration races a submit against a day-gated write that starts just after it.
 * Pre-fix submit holds no day lock during its reads, so the racing write commits onto
 * the still-open day and the frozen snapshot omits it (or SERIALIZABLE aborts). Post-fix
 * submit already holds the day lock, so the racing write blocks, then sees REMITTED and
 * is rejected — every snapshot stays equal to the live sums with no 500.
 */
class RemittanceSubmitDayLockPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "submit-lock-caller")
        IdentityFixtures.insertTestUser(targetUserId, "submit-lock-target")
    }

    @Test
    fun `expense racing submit never slips past the frozen snapshot`() {
        repeat(RACE_ITERATIONS) { iteration ->
            val iterBranchId = TestFixtures.uuid()
            BranchWorkforceFixtures.insertTestBranch(iterBranchId, "Submit Race Branch ${TestFixtures.uuid()}")
            IdentityFixtures.grantSubmitRemittance(callerId, sourceId, iterBranchId)
            val dayId = BranchWorkforceFixtures.createBranchDayForToday(iterBranchId)
            val remittanceId = createDraftCovering(iterBranchId, dayId)
            val version = RemittanceService.getRemittance(remittanceId).remittance.version

            var expenseFailure: Throwable? = null
            val racer =
                thread {
                    Thread.sleep(RACE_DELAY_MILLIS)
                    expenseFailure =
                        runCatching {
                            ExpenseService.create(
                                callerId = callerId,
                                id = TestFixtures.uuid(),
                                branchDayId = dayId,
                                amount = EXPENSE_AMOUNT,
                                category = ExpenseCategory.TRANSPORTATION,
                                notes = null,
                            )
                        }.exceptionOrNull()
                }
            val submitFailure =
                runCatching { RemittanceService.submit(callerId, remittanceId, version) }.exceptionOrNull()
            racer.join(BARRIER_JOIN_MILLIS)

            assertNoServerError(expenseFailure, "expense", iteration)
            assertNull(submitFailure, "iter $iteration: submit failed on legitimate concurrent use: $submitFailure")
            assertSnapshotEqualsLive(
                iteration,
                remittanceId,
                listOf(dayId),
                RemittanceRepository::calculateExpenseSum,
                RemittanceFinancialSnapshot::totalExpenses,
            )
        }
    }

    @Test
    fun `compensation racing submit never slips past the frozen snapshot`() {
        repeat(RACE_ITERATIONS) { iteration ->
            val iterBranchId = TestFixtures.uuid()
            BranchWorkforceFixtures.insertTestBranch(iterBranchId, "Submit Race Branch ${TestFixtures.uuid()}")
            IdentityFixtures.grantSubmitRemittance(callerId, sourceId, iterBranchId)
            val dayId = BranchWorkforceFixtures.createBranchDayForToday(iterBranchId)
            val remittanceId = createDraftCovering(iterBranchId, dayId)
            val version = RemittanceService.getRemittance(remittanceId).remittance.version

            var compensationFailure: Throwable? = null
            val racer =
                thread {
                    Thread.sleep(RACE_DELAY_MILLIS)
                    compensationFailure =
                        runCatching {
                            CompensationService.create(
                                callerId = callerId,
                                id = TestFixtures.uuid(),
                                workBranchDayId = dayId,
                                payingBranchDayId = dayId,
                                userId = targetUserId,
                                amount = COMPENSATION_AMOUNT,
                                note = null,
                            )
                        }.exceptionOrNull()
                }
            val submitFailure =
                runCatching { RemittanceService.submit(callerId, remittanceId, version) }.exceptionOrNull()
            racer.join(BARRIER_JOIN_MILLIS)

            assertNoServerError(compensationFailure, "compensation", iteration)
            assertNull(submitFailure, "iter $iteration: submit failed on legitimate concurrent use: $submitFailure")
            assertSnapshotEqualsLive(
                iteration,
                remittanceId,
                listOf(dayId),
                RemittanceRepository::calculateCompensationSum,
                RemittanceFinancialSnapshot::totalCompensation,
            )
        }
    }

    private fun createDraftCovering(
        iterBranchId: UUID,
        dayId: UUID,
    ): UUID {
        val remittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = iterBranchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = TestFixtures.today,
            dateRangeEnd = TestFixtures.today,
        )
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittanceId,
            id = TestFixtures.uuid(),
            branchDayId = dayId,
        )
        return remittanceId
    }

    private fun assertNoServerError(
        failure: Throwable?,
        what: String,
        iteration: Int,
    ) {
        assertTrue(
            failure == null ||
                failure is ForbiddenException ||
                failure is ValidationException,
            "iter $iteration: racing $what neither committed nor cleanly rejected: $failure",
        )
    }

    private fun assertSnapshotEqualsLive(
        iteration: Int,
        remittanceId: UUID,
        days: List<UUID>,
        liveSum: (List<UUID>) -> BigDecimal,
        frozenSum: (RemittanceFinancialSnapshot) -> BigDecimal,
    ) {
        val snapshot =
            assertNotNull(
                RemittanceFinancialSnapshotRepository.findByRemittanceId(remittanceId),
                "iter $iteration: SESSION submit writes a snapshot",
            )
        assertEquals(
            0,
            liveSum(days).compareTo(frozenSum(snapshot)),
            "iter $iteration: frozen snapshot omits a write committed before the day transition " +
                "(live=${liveSum(days)}, frozen=${frozenSum(snapshot)})",
        )
    }

    companion object {
        private val EXPENSE_AMOUNT = BigDecimal("500.00")
        private val COMPENSATION_AMOUNT = BigDecimal("1500.00")
        private const val RACE_ITERATIONS = 30
        private const val RACE_DELAY_MILLIS = 8L
        private const val BARRIER_JOIN_MILLIS = 30000L
    }
}
