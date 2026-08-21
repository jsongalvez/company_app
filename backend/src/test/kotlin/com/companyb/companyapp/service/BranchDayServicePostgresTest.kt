package com.companyb.companyapp.service
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BranchDayServicePostgresTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()

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
        assertEquals(TestFixtures.today, branchDay.date)
    }

    @Test
    fun `getToday creates missing day idempotently`() {
        val first = BranchDayService.getToday(branchId)
        val second = BranchDayService.getToday(branchId)

        assertEquals(first.id, second.id)
        assertEquals(DayStatus.OPEN, second.status)
        assertEquals(TestFixtures.today, second.date)
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
            BranchDayService.getToday(TestFixtures.uuid())
        }
    }

    @Test
    fun `historical day gates evaluate through the central operational-date policy`() {
        val yesterday = TestFixtures.today.minusDays(1)
        val yesterdayId =
            DatabaseTestHelper.createBranchDayForDate(branchId, yesterday)

        // Persisted status is still OPEN; the effective evaluation must treat it as PAST.
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq yesterdayId }) {
                it[BranchDayTable.status] = DayStatus.OPEN
            }
        }

        assertEquals(DayStatus.PAST, BranchDayService.getEffectiveStatus(yesterdayId))
        assertFailsWith<ForbiddenException> {
            BranchDayService.checkBranchDayReadable(TestFixtures.uuid(), yesterdayId)
        }
        assertFailsWith<ForbiddenException> {
            BranchDayService.checkBranchDayEditable(TestFixtures.uuid(), yesterdayId)
        }
    }
}
