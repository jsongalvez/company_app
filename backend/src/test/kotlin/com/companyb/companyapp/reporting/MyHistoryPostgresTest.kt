package com.companyb.companyapp.reporting

import com.companyb.companyapp.contracts.reporting.EarningKind
import com.companyb.companyapp.finance.AllowanceTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyHistoryPostgresTest : ExportServicePostgresBase() {
    @Test
    fun `shows only the caller's own shifts and money`() {
        val other = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(other, "someone-else")
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, callerId)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, other)
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, callerId, BigDecimal("500.00"), assignedBy = other)
        CommerceFinanceFixtures.insertTestCompensation(branchDayId, other, BigDecimal("900.00"), assignedBy = callerId)
        val dayId = branchDayId
        val me = callerId
        transaction {
            AllowanceTable.insert {
                it[AllowanceTable.id] = TestFixtures.uuid()
                it[AllowanceTable.branchDayId] = dayId
                it[AllowanceTable.userId] = me
                it[AllowanceTable.amount] = BigDecimal("150.00")
                it[AllowanceTable.assignedBy] = other
            }
        }

        val history = MyHistoryRepository.load(callerId, today, today)

        assertEquals(1, history.shifts.size)
        assertEquals(today.toString(), history.shifts.single().date)
        assertEquals(listOf(EarningKind.PAY, EarningKind.ALLOWANCE), history.earnings.map { it.kind })
        assertEquals("650.00", history.totalEarned)
        assertTrue(history.earnings.all { it.branchName.startsWith("Export Test Branch") })
    }

    @Test
    fun `nothing outside the dates`() {
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, callerId)
        val history = MyHistoryRepository.load(callerId, today.minusDays(10), today.minusDays(1))
        assertEquals(0, history.shifts.size)
        assertEquals("0.00", history.totalEarned)
    }
}
