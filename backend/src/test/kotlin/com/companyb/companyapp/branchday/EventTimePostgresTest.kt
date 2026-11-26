package com.companyb.companyapp.branchday

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.NewProductSale
import com.companyb.companyapp.commerce.ProductSaleService
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceService
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Saves made offline and sent later count at the time they happened: the commission split, the
 * duty times and the closed-day rule all read the phone's `X-Occurred-At`, within bounds.
 */
class EventTimePostgresTest : BasePostgresTest() {
    private val early = TestFixtures.uuid()
    private val late = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val yesterday: LocalDate = BranchDayService.currentOperationalDate().minusDays(1)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(early, "early")
        IdentityFixtures.insertTestUser(late, "late")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Event Time Branch")
        val categoryId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(categoryId, "Event Time Category")
        CommerceFinanceFixtures.insertTestProduct(productId, "Event Time Product", categoryId)
        transaction { CommerceFinanceFixtures.insertTestCard(branchId, productId, 10) }
        IdentityFixtures.member(early, branchId)
        IdentityFixtures.member(late, branchId)
    }

    @AfterTest
    fun clearClock() = OpTime.clear()

    /** Yesterday at [hour]:[minute] Manila, which is always less than 48 hours ago. */
    private fun at(
        hour: Int,
        minute: Int = 0,
    ): Instant = yesterday.atTime(hour, minute).atZone(BranchDayService.manilaZone).toInstant()

    private fun clockIn(
        user: UUID,
        occurred: Instant?,
    ): UUID {
        OpTime.set(occurred)
        return AttendanceService.clockIn(TestFixtures.uuid(), branchId, user).id
    }

    private fun clockOut(
        user: UUID,
        attendance: UUID,
        occurred: Instant,
    ) {
        OpTime.set(occurred)
        AttendanceService.clockOut(attendance, user)
    }

    private fun sell(
        occurred: Instant,
        dayId: UUID,
    ) {
        OpTime.set(occurred)
        ProductSaleService.sell(
            callerId = early,
            NewProductSale(
                id = TestFixtures.uuid(),
                branchDayId = dayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
            ),
            expectedVersion = null,
        )
    }

    private fun flaggedRowsFor(record: UUID): List<Pair<Boolean, String?>> =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.recordId eq record }
                .map { it[AuditLogTable.isFlagged] to it[AuditLogTable.reason] }
        }

    @Test
    fun `a sale sent late splits among who was on duty when it happened`() {
        val shift = clockIn(early, at(10))
        clockOut(early, shift, at(11, 30))
        clockIn(late, at(11, 45))
        val dayId = BranchDayService.findByBranchAndDate(branchId, yesterday)!!.id
        sell(at(11), dayId)

        val splits = CommissionService.getByBranchDayId(dayId).associate { it.userId to it.amount.signum() }
        assertEquals(mapOf(early to 1), splits.filterValues { it > 0 })
    }

    @Test
    fun `a clock-in keeps when it happened and when it arrived`() {
        val id = clockIn(early, at(9))
        val row = transaction { AttendanceTable.selectAll().where { AttendanceTable.id eq id }.single() }
        assertEquals(at(9), row[AttendanceTable.clockIn].toInstant())
        assertTrue(row[AttendanceTable.clockInRecordedAt].toInstant().isAfter(at(9)))
    }

    @Test
    fun `work from yesterday sent today lands by itself, flagged for review`() {
        val id = clockIn(early, at(9))
        val rows = flaggedRowsFor(id)
        assertEquals(1, rows.size)
        assertTrue(rows.single().first, "late save is flagged")
        assertTrue(
            rows
                .single()
                .second
                .orEmpty()
                .startsWith("Saved offline on $yesterday"),
        )
    }

    @Test
    fun `a save older than the late window still needs the closed-day right`() {
        val old = BranchDayService.currentOperationalDate().minusDays(3)
        OpTime.set(old.atTime(10, 0).atZone(BranchDayService.manilaZone).toInstant())
        assertFailsWith<ForbiddenException> { AttendanceService.clockIn(TestFixtures.uuid(), branchId, early) }
    }

    @Test
    fun `a remitted day takes no late save`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, yesterday)
        transaction { BranchDayTable.update({ BranchDayTable.id eq dayId }) { it[status] = DayStatus.REMITTED } }
        OpTime.set(at(10))
        assertFailsWith<ForbiddenException> { AttendanceService.clockIn(TestFixtures.uuid(), branchId, early) }
    }

    @Test
    fun `a phone clock ahead of the server counts as now`() {
        OpTime.set(Instant.now().plusSeconds(3600))
        val id = AttendanceService.clockIn(TestFixtures.uuid(), branchId, early).id
        val row = transaction { AttendanceTable.selectAll().where { AttendanceTable.id eq id }.single() }
        assertTrue(!row[AttendanceTable.clockIn].toInstant().isAfter(Instant.now()))
    }
}
