package com.companyb.companyapp.session

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.session.BookingStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VisitBookingPostgresTest : BasePostgresTest() {
    private val booker = TestFixtures.uuid()
    private val mover = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranch = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(booker, "booker")
        IdentityFixtures.insertTestUser(mover, "mover")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        BranchWorkforceFixtures.insertTestBranch(otherBranch)
        SessionClientFixtures.insertTestClient(clientId)
    }

    private fun today() = BranchDayService.currentOperationalDate()

    @Test
    fun `booking a future visit lists it for the branch and the client, and a retry does not double it`() {
        val id = TestFixtures.uuid()
        val input = BookingInput(today().plusDays(3), LocalTime.of(14, 30), " follow-up ")

        val (first, created) = VisitBookingService.create(booker, id, branchId, clientId, input)
        val (_, again) = VisitBookingService.create(booker, id, branchId, clientId, input)

        assertTrue(created)
        assertEquals(false, again)
        assertEquals("follow-up", first.note)
        assertEquals("14:30", first.visitTime)
        assertEquals(listOf(id.toString()), VisitBookingService.upcoming(branchId).map { it.id })
        assertEquals(listOf(id.toString()), VisitBookingService.forClient(clientId).map { it.id })
        assertTrue(VisitBookingService.upcoming(otherBranch).isEmpty())
        assertEquals(1, auditRows(id))
    }

    @Test
    fun `moving and cancelling records who changed it and drops it from upcoming`() {
        val id = TestFixtures.uuid()
        VisitBookingService.create(booker, id, branchId, clientId, BookingInput(today(), null, null))

        val moved =
            VisitBookingService.update(
                mover,
                id,
                branchId,
                BookingInput(today().plusDays(7), null, null),
                BookingStatus.BOOKED,
            )
        assertEquals(today().plusDays(7).toString(), moved.visitDate)
        assertEquals(moved.createdByName.isNotBlank(), true)
        assertTrue(moved.updatedByName != null && moved.updatedByName != moved.createdByName)

        VisitBookingService.update(
            mover,
            id,
            branchId,
            BookingInput(today().plusDays(7), null, null),
            BookingStatus.CANCELLED,
        )
        assertTrue(VisitBookingService.upcoming(branchId).isEmpty())
        assertEquals(BookingStatus.CANCELLED, VisitBookingService.forClient(clientId).single().status)
        assertEquals(3, auditRows(id))
    }

    @Test
    fun `past days, unknown clients, other branches and reused ids are refused`() {
        assertFailsWith<ValidationException> {
            VisitBookingService.create(
                booker,
                TestFixtures.uuid(),
                branchId,
                clientId,
                BookingInput(today().minusDays(1), null, null),
            )
        }
        assertFailsWith<NotFoundException> {
            VisitBookingService.create(
                booker,
                TestFixtures.uuid(),
                branchId,
                TestFixtures.uuid(),
                BookingInput(today(), null, null),
            )
        }
        val id = TestFixtures.uuid()
        VisitBookingService.create(booker, id, branchId, clientId, BookingInput(today(), null, null))
        assertFailsWith<NotFoundException> {
            VisitBookingService.update(
                booker,
                id,
                otherBranch,
                BookingInput(today(), null, null),
                BookingStatus.CANCELLED,
            )
        }
        assertFailsWith<ConflictException> {
            VisitBookingService.create(booker, id, otherBranch, clientId, BookingInput(today(), null, null))
        }
    }

    private fun auditRows(id: java.util.UUID): Int =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.recordId eq id }
                .count()
                .toInt()
        }
}
