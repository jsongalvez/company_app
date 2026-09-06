package com.companyb.companyapp.reporting
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
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
    private val branchId = TestFixtures.uuid()
    private val today = TestFixtures.today

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId, "Browse Test Branch")
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
                branchId = TestFixtures.uuid(),
                cursor = null,
                limit = 10,
            )
        }
    }

    @Test
    fun `from window admits the boundary day and excludes older days`() {
        val older = today.minusDays(3)
        val boundary = today.minusDays(2)
        val newer = today.minusDays(1)
        insertBranchDay(older)
        insertBranchDay(boundary)
        insertBranchDay(newer)

        val response = browse(limit = 10, from = boundary)

        assertEquals(listOf(newer, boundary), response.entries.map { LocalDate.parse(it.date) })
    }

    @Test
    fun `to window admits the boundary day and excludes newer days`() {
        val older = today.minusDays(3)
        val boundary = today.minusDays(2)
        val newer = today.minusDays(1)
        insertBranchDay(older)
        insertBranchDay(boundary)
        insertBranchDay(newer)

        val response = browse(limit = 10, to = boundary)

        assertEquals(listOf(boundary, older), response.entries.map { LocalDate.parse(it.date) })
    }

    @Test
    fun `from and to window admits only days inside the range`() {
        val outsideLow = today.minusDays(5)
        val insideLow = today.minusDays(4)
        val insideHigh = today.minusDays(2)
        val outsideHigh = today.minusDays(1)
        listOf(outsideLow, insideLow, insideHigh, outsideHigh).forEach(::insertBranchDay)

        val response = browse(limit = 10, from = insideLow, to = insideHigh)

        assertEquals(listOf(insideHigh, insideLow), response.entries.map { LocalDate.parse(it.date) })
    }

    @Test
    fun `windowed cursor walk stays inside the window`() {
        (0L..6L).forEach { offset -> insertBranchDay(today.minusDays(offset)) }

        val dates = mutableListOf<String>()
        var cursor: String? = null
        do {
            val response = browse(limit = 2, cursor = cursor, from = today.minusDays(6), to = today.minusDays(2))
            dates += response.entries.map { it.date }
            cursor = response.nextCursor
        } while (cursor != null)

        assertEquals(
            (2L..6L).map { today.minusDays(it) },
            dates.map { LocalDate.parse(it) },
            "the walk pages the 5 in-window days newest-first and never crosses the window",
        )
    }

    @Test
    fun `window with no matching days returns empty feed`() {
        insertBranchDay(today)

        val response = browse(limit = 10, from = today.minusDays(10), to = today.minusDays(5))

        assertTrue(response.entries.isEmpty())
        assertNull(response.nextCursor)
    }

    private fun browse(
        limit: Int,
        cursor: String? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): DailySalesSummaryBrowseResponse =
        DailySalesSummaryService.browseDailySummaries(
            branchId = branchId,
            cursor = cursor?.let { decodeDailySummaryCursor(it) },
            limit = limit,
            from = from,
            to = to,
        )

    private fun insertBranchDay(date: LocalDate) {
        val id = TestFixtures.uuid()
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.id] = id
                // Receiver is BranchDayTable: unqualified `branchId` would
                // resolve to the COLUMN (class properties lose — #133 lesson).
                it[BranchDayTable.branchId] = this@DailySalesSummaryBrowsePostgresTest.branchId
                it[BranchDayTable.date] = date
            }
        }
    }
}
