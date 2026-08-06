package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BranchDayServicePostgresTest : BasePostgresTest() {
    private val branchId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestBranch(branchId, "Test Branch Day Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
    }

    @Test
    fun `getToday returns existing day for today`() {
        val existingId = DatabaseTestHelper.createBranchDayForToday(branchId)

        val branchDay = BranchDayService.getToday(branchId)

        assertEquals(existingId, branchDay.id)
        assertEquals(DayStatus.OPEN, branchDay.status)
        assertEquals(LocalDate.now(BranchDayService.manilaZone), branchDay.date)
    }

    @Test
    fun `getToday creates missing day idempotently`() {
        val first = BranchDayService.getToday(branchId)
        val second = BranchDayService.getToday(branchId)

        assertEquals(first.id, second.id)
        assertEquals(DayStatus.OPEN, second.status)
        assertEquals(LocalDate.now(BranchDayService.manilaZone), second.date)
    }

    @Test
    fun `getToday reports REMITTED status for remitted day`() {
        DatabaseTestHelper.createBranchDayForToday(branchId)
        transaction {
            BranchDayTable.update({ BranchDayTable.branchId eq branchId }) {
                it[BranchDayTable.status] = DayStatus.REMITTED
            }
        }

        val branchDay = BranchDayService.getToday(branchId)

        assertEquals(DayStatus.REMITTED, branchDay.status)
    }

    @Test
    fun `getToday throws NotFound for missing branch`() {
        assertFailsWith<NotFoundException> {
            BranchDayService.getToday(UUID.randomUUID())
        }
    }
}
