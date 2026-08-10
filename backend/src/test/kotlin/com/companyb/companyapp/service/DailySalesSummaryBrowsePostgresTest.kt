package com.companyb.companyapp.service

import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.decodeDailySummaryCursor
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailySalesSummaryBrowsePostgresTest : BasePostgresTest() {
    private val branchId = UUID.randomUUID()
    private val today = LocalDate.now(ZoneId.of("Asia/Manila"))

    override fun initTestData() {
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestBranch(branchId, "Browse Test Branch")
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
    }

    @Test
    fun `returns days in DESC order`() {
        val d1 = today.minusDays(2)
        val d2 = today.minusDays(1)
        val d3 = today
        insertBranchDay(d1)
        insertBranchDay(d2)
        insertBranchDay(d3)

        val response = browse(limit = 10)

        assertEquals(3, response.entries.size)
        assertEquals(listOf(d3, d2, d1), response.entries.map { LocalDate.parse(it.date) })
        assertNull(response.nextCursor, "no more pages within one page")
    }

    @Test
    fun `honors limit and exposes nextCursor`() {
        insertBranchDay(today.minusDays(2))
        insertBranchDay(today.minusDays(1))
        insertBranchDay(today)

        val response = browse(limit = 1)

        assertEquals(1, response.entries.size)
        assertEquals(today, LocalDate.parse(response.entries.single().date))
        assertNotNull(response.nextCursor, "more pages remain after limit=1")
    }

    @Test
    fun `cursor walk yields every day exactly once`() {
        (0L..4L).forEach { offset -> insertBranchDay(today.minusDays(offset)) }

        val dates = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val response = browse(limit = 2, cursor = cursor)
            dates += response.entries.map { it.date }
            cursor = response.nextCursor
            pages++
        } while (cursor != null)

        assertEquals(5, dates.size)
        assertEquals(5, dates.distinct().size, "cursor pagination must not duplicate or skip rows")
        assertEquals(
            (0L..4L).map { today.minusDays(it) },
            dates.map { LocalDate.parse(it) },
            "feed must be newest-first",
        )
        assertTrue(pages >= 3)
    }

    @Test
    fun `zero-activity days are included as zero rows`() {
        insertBranchDay(today.minusDays(1))
        insertBranchDay(today)

        val response = browse(limit = 10)

        assertEquals(2, response.entries.size)
        response.entries.forEach { entry ->
            assertEquals("0.00", entry.grossIncome)
            assertEquals("0.00", entry.totalCompensation)
            assertEquals("0.00", entry.totalExpenses)
            assertEquals("0.00", entry.netIncome)
            assertEquals("0.00", entry.totalProductSales)
            assertEquals("0.0000", entry.totalCommission)
        }
    }

    @Test
    fun `returns empty feed when branch has no days`() {
        val response = browse(limit = 10)

        assertTrue(response.entries.isEmpty())
        assertNull(response.nextCursor)
    }

    @Test
    fun `throws 404 when branch does not exist`() {
        assertFailsWith<NotFoundException> {
            DailySalesSummaryService.browseDailySummaries(
                branchId = UUID.randomUUID(),
                cursor = null,
                limit = 10,
            )
        }
    }

    private fun browse(
        limit: Int,
        cursor: String? = null,
    ): DailySalesSummaryBrowseResponse =
        DailySalesSummaryService.browseDailySummaries(
            branchId = branchId,
            cursor = cursor?.let { decodeDailySummaryCursor(it) },
            limit = limit,
        )

    private fun insertBranchDay(date: LocalDate) {
        val id = UUID.randomUUID()
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.id] = id
                // Receiver is BranchDayTable: unqualified `branchId` would
                // resolve to the COLUMN (class properties lose — #133 lesson).
                it[BranchDayTable.branchId] = this@DailySalesSummaryBrowsePostgresTest.branchId
                it[BranchDayTable.date] = date
            }
        }
        trackOwned(BranchDayTable, BranchDayTable.id, id)
    }
}
