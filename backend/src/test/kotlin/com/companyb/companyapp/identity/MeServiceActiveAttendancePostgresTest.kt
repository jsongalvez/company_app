package com.companyb.companyapp.identity
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceRepository
import com.companyb.companyapp.workforce.AttendanceService
import com.companyb.companyapp.workforce.ClockInParams
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeServiceActiveAttendancePostgresTest : BasePostgresTest() {
    private val userId = TestFixtures.uuid()
    private val inactiveUserId = TestFixtures.uuid()
    private val branchA = TestFixtures.uuid()
    private val branchB = TestFixtures.uuid()
    private val reliefBranch = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(userId, "me-active")
        IdentityFixtures.insertUser(
            id = inactiveUserId,
            username = "inactive-$inactiveUserId",
            passwordHash = "test-password-hash",
            email = "${inactiveUserId.toString().take(8)}@t.st",
            displayName = "Inactive User",
            status = UserStatus.INACTIVE,
        )
        BranchWorkforceFixtures.insertTestBranch(branchA, "Branch A")
        BranchWorkforceFixtures.insertTestBranch(branchB, "Branch B")
        BranchWorkforceFixtures.insertTestBranch(reliefBranch, "Relief Branch")
    }

    @Test
    fun `getActiveAttendance returns null shift when never clocked in`() {
        assignToBranch(branchA)

        val response = MeService.getActiveAttendance(userId)

        assertNull(response.shift)
    }

    @Test
    fun `getActiveAttendance returns the exact active shift identifiers`() {
        assignToBranch(branchA)
        val clockIn = AttendanceService.clockIn(TestFixtures.uuid(), branchA, userId)
        val branchDayId = BranchDayService.findToday(branchA)?.id

        val shift = MeService.getActiveAttendance(userId).shift

        assertNotNull(shift)
        assertEquals(clockIn.id.toString(), shift.attendanceId)
        assertEquals(branchA.toString(), shift.branchId)
        assertEquals("Branch A", shift.branchName)
        assertEquals(branchDayId.toString(), shift.branchDayId)
        assertEquals(TestFixtures.today.toString(), shift.date)
        assertEquals(false, shift.isRelief)
    }

    @Test
    fun `getActiveAttendance returns null after clock-out`() {
        assignToBranch(branchA)
        val clockIn = AttendanceService.clockIn(TestFixtures.uuid(), branchA, userId)
        AttendanceService.clockOut(clockIn.id, userId)

        assertNull(MeService.getActiveAttendance(userId).shift)
    }

    @Test
    fun `getActiveAttendance ignores an open window from a previous day`() {
        assignToBranch(branchA)
        val yesterday = TestFixtures.today.minusDays(1)
        val staleBranchDay = BranchDayService.resolveOrCreate(branchA, yesterday)
        transaction {
            AttendanceRepository.clockInInTransaction(
                ClockInParams(
                    attendanceId = TestFixtures.uuid(),
                    branchDayId = staleBranchDay.id,
                    userId = userId,
                    markedBy = userId,
                    branchDayAssignmentId = TestFixtures.uuid(),
                    isRelief = false,
                    branchId = branchA,
                ),
            )
        }

        assertNull(MeService.getActiveAttendance(userId).shift)
    }

    @Test
    fun `getActiveAttendance reports relief clock-in with isRelief true`() {
        AttendanceService.clockIn(TestFixtures.uuid(), reliefBranch, userId)

        val shift = MeService.getActiveAttendance(userId).shift

        assertNotNull(shift)
        assertEquals(reliefBranch.toString(), shift.branchId)
        assertTrue(shift.isRelief)
    }

    @Test
    fun `getActiveAttendance still returns an open window after the assignment ended`() {
        assignToBranch(branchA)
        AttendanceService.clockIn(TestFixtures.uuid(), branchA, userId)
        BranchWorkforceFixtures.insertTestAssignment(
            userId = userId,
            branchId = branchA,
            slot = 1,
            assignedBy = userId,
            ended = true,
        )

        // Resume is an attendance fact; access stays capability-gated (#669).
        assertNotNull(MeService.getActiveAttendance(userId).shift)
    }

    @Test
    fun `getActiveAttendance prefers the most recent window across branches`() {
        assignToBranch(branchA)
        assignToBranch(branchB)
        AttendanceService.clockIn(TestFixtures.uuid(), branchA, userId)
        AttendanceService.clockIn(TestFixtures.uuid(), branchB, userId)

        val shift = MeService.getActiveAttendance(userId).shift

        assertNotNull(shift)
        assertEquals(branchB.toString(), shift.branchId)
    }

    @Test
    fun `getActiveAttendance throws ForbiddenException for INACTIVE user`() {
        assertFailsWith<ForbiddenException> {
            MeService.getActiveAttendance(inactiveUserId)
        }
    }

    @Test
    fun `getActiveAttendance throws NotFoundException for non-existent user`() {
        assertFailsWith<NotFoundException> {
            MeService.getActiveAttendance(TestFixtures.uuid())
        }
    }

    private fun assignToBranch(branchId: UUID): UUID =
        BranchWorkforceFixtures.insertTestAssignment(
            userId = userId,
            branchId = branchId,
            slot = 1,
            assignedBy = userId,
        )
}
