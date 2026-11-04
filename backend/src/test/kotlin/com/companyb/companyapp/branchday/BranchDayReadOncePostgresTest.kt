package com.companyb.companyapp.branchday

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A request reads a branch day once, and its own write to `branch_day` makes the next read fresh. */
class BranchDayReadOncePostgresTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val date = LocalDate.of(2026, 1, 3)

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId, "Day Once $branchId")
    }

    @Test
    fun `a missing day is read once, then seen once the request makes it`() {
        inRequest {
            assertNull(BranchDayRepository.findByBranchAndDate(branchId, date))
            assertNull(BranchDayRepository.findByBranchAndDate(branchId, date))
            assertEquals(1, RequestQueryTally.snapshot().queries)
            val made = BranchDayRepository.resolveOrCreate(branchId, date)
            assertEquals(made, BranchDayRepository.findByBranchAndDate(branchId, date))
            assertEquals(made, BranchDayRepository.resolveOrCreate(branchId, date))
        }
    }

    @Test
    fun `a status write in the request is seen by the next read`() {
        inRequest {
            val day = BranchDayRepository.resolveOrCreate(branchId, date)
            assertEquals(day, BranchDayRepository.findById(day.id))
            transaction { BranchDayRepository.updateStatusInTransaction(day.id, DayStatus.REMITTED) }
            assertEquals(DayStatus.REMITTED, BranchDayRepository.findById(day.id)?.status)
            assertEquals(DayStatus.REMITTED, BranchDayRepository.findByBranchAndDate(branchId, date)?.status)
        }
    }

    private fun inRequest(block: () -> Unit) {
        Database.connect(JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource()))
        try {
            RequestQueryTally.startRequest()
            block()
        } finally {
            RequestQueryTally.endRequest()
            Database.connect(TestDatabaseLifecycle.requireTestDataSource())
        }
    }
}
