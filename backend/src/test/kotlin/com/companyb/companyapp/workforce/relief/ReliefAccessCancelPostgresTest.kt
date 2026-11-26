package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Withdrawing and cancelling a relief ask, and the one-duty-per-day guards. */
class ReliefAccessCancelPostgresTest : ReliefAccessServicePostgresBase() {
    private fun insertAttendance(
        id: UUID,
        userId: UUID,
        dayId: UUID,
    ) {
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = id
                it[AttendanceTable.branchDayId] = dayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = userId
                it[AttendanceTable.clockIn] = TestFixtures.now
            }
        }
    }

    // ──────────────────────────────────────────────
    // Cancel / withdraw lock matrix (#357 owner rules)
    // ──────────────────────────────────────────────

    @Test
    fun `requester withdraws own pending ask`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.cancelRequest(requestId, reliefUserId)

        assertEquals(ReliefAccessStatus.CANCELLED, result.requestStatus)
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `any branch member cancels someone else's pending ask`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.cancelRequest(requestId, memberId)

        assertEquals(ReliefAccessStatus.CANCELLED, result.requestStatus)
    }

    @Test
    fun `cancel by a non-member non-requester fails with 403`() {
        val outsider = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsider, "cancel-outsider")
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.cancelRequest(requestId, outsider)
        }
    }

    @Test
    fun `withdraw locks once the requester clocks in as relief`() {
        val attendanceId = TestFixtures.uuid()
        insertAttendance(attendanceId, reliefUserId, branchDayId)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ValidationException> {
            ReliefAccessService.cancelRequest(requestId, reliefUserId)
        }
        assertFailsWith<ValidationException> {
            ReliefAccessService.cancelRequest(requestId, memberId)
        }
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
    }

    @Test
    fun `cancel on decided request fails with 409`() {
        val grantedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(grantedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(grantedId, memberId)

        assertFailsWith<ConflictException> {
            ReliefAccessService.cancelRequest(grantedId, reliefUserId)
        }
    }

    // ──────────────────────────────────────────────
    // One-duty-per-day guards (#913)
    // ──────────────────────────────────────────────

    @Test
    fun `re-requesting an already granted day fails with 409`() {
        val firstId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(firstId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(firstId, memberId)

        assertFailsWith<ConflictException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, TestFixtures.today, reliefUserId)
        }
    }

    @Test
    fun `granting a second pending for a granted user fails with 409 instead of 500`() {
        val firstId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(firstId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(firstId, memberId)

        // A PENDING row predating the grant (seeded directly so the
        // creation-time guard cannot see the future duty). Distinct-name local:
        // inside insert{}, a bare branchDayId would resolve to the table COLUMN
        // via the implicit table receiver (the #356 lesson).
        val secondId = TestFixtures.uuid()
        val seededDay = branchDayId
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = secondId
                it[GrantReliefAccessTable.branchDayId] = seededDay
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }

        assertFailsWith<ConflictException> {
            ReliefAccessService.grantAccess(secondId, memberId)
        }
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(secondId)?.requestStatus)
    }

    @Test
    fun `granting a request after the user accepted an invite for the day fails with 409`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        val invite = ReliefInviteService.createInvite(memberId, branchId, reliefUserId, TestFixtures.today)
        ReliefInviteService.acceptInvite(reliefUserId, invite.id)
        assertTrue(
            ReliefInviteRepository.hasActiveGrant(reliefUserId, branchDayId),
            "precondition: invite path holds the day",
        )

        assertFailsWith<ConflictException> {
            ReliefAccessService.grantAccess(requestId, memberId)
        }
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
    }

    @Test
    fun `accepting an invite after the user was granted for the day fails with 409`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(requestId, memberId)

        // A PENDING invite predating the grant (seeded directly so the
        // creation-time advisory cannot see the future duty).
        val inviteId = TestFixtures.uuid()
        val seededDay = branchDayId
        transaction {
            ReliefInviteRepository.insertInTransaction(
                id = inviteId,
                branchDayId = seededDay,
                invitedBy = memberId,
                invitee = reliefUserId,
            )
        }

        assertFailsWith<ConflictException> {
            ReliefInviteService.acceptInvite(reliefUserId, inviteId)
        }
        assertEquals(ReliefInviteStatus.PENDING, ReliefInviteRepository.findById(inviteId)?.status)
    }

    @Test
    fun `granting a denied or cancelled request fails with 409`() {
        val deniedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(deniedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.denyAccess(deniedId, memberId)
        assertFailsWith<ConflictException> {
            ReliefAccessService.grantAccess(deniedId, memberId)
        }

        val cancelledId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(cancelledId, branchId, TestFixtures.today.plusDays(2), reliefUserId)
        ReliefAccessService.cancelRequest(cancelledId, reliefUserId)
        assertFailsWith<ConflictException> {
            ReliefAccessService.grantAccess(cancelledId, memberId)
        }
    }

    @Test
    fun `cancel on non-existent request fails with 404`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.cancelRequest(TestFixtures.uuid(), memberId)
        }
    }
}
