package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Raising a relief ask, one live ask per branch day, and who reads which asks. */
class ReliefAccessServicePostgresTest : ReliefAccessServicePostgresBase() {
    private val inactiveRequester = TestFixtures.uuid()

    private fun requestExists(requestId: UUID): Boolean =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .empty()
                .not()
        }

    // ──────────────────────────────────────────────
    // Request lifecycle (#357: branch + date picker)
    // ──────────────────────────────────────────────

    @Test
    fun `successful request persists with PENDING status and writes audit`() {
        val requestId = TestFixtures.uuid()

        val result =
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.PENDING, result.requestStatus)
        assertEquals(reliefUserId, result.requestedBy)
        assertTrue(requestExists(requestId))
        assertEquals(1L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `future-dated request resolves its own branch day and stays PENDING`() {
        val futureDate = TestFixtures.today.plusDays(FUTURE_DAYS)
        val futureRequestId = TestFixtures.uuid()

        val result = ReliefAccessService.requestReliefAccess(futureRequestId, branchId, futureDate, reliefUserId)
        trackRequest(futureRequestId)

        assertEquals(ReliefAccessStatus.PENDING, result.requestStatus)
        val day = BranchDayService.requireBranchDayExists(result.branchDayId)
        assertEquals(futureDate, day.date)
    }

    @Test
    fun `null date defaults to the current operational day`() {
        val requestId = TestFixtures.uuid()

        val result = ReliefAccessService.requestReliefAccess(requestId, branchId, null, reliefUserId)

        val day = BranchDayService.requireBranchDayExists(result.branchDayId)
        assertEquals(TestFixtures.today, day.date)
    }

    @Test
    fun `past-dated request fails with 400`() {
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today.minusDays(1), reliefUserId)
        }
        assertFalse(requestExists(requestId))
    }

    @Test
    fun `request by an assigned member of the branch fails with 400`() {
        val assignedMember = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(assignedMember, "home-member")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = assignedMember,
            branchId = branchId,
            slot = 2,
            assignedBy = memberId,
        )

        assertFailsWith<ValidationException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, null, assignedMember)
        }
    }

    @Test
    fun `request fails with 403 when requester is deactivated`() {
        IdentityFixtures.insertTestUser(inactiveRequester, "inactive")
        transaction {
            AppUserTable.update({ AppUserTable.id eq inactiveRequester }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, null, inactiveRequester)
        }
    }

    @Test
    fun `request on non-existent branch fails with 404`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), TestFixtures.uuid(), null, reliefUserId)
        }
    }

    @Test
    fun `duplicate live request id is an idempotent replay`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val duplicate =
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.PENDING, duplicate.requestStatus)
    }

    // ──────────────────────────────────────────────
    // Flood control (#352 Q4: one live ask per branch day)
    // ──────────────────────────────────────────────

    @Test
    fun `second live request for the same branch day fails with 409`() {
        ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ConflictException> {
            ReliefAccessService.requestReliefAccess(TestFixtures.uuid(), branchId, TestFixtures.today, reliefUserId)
        }
    }

    @Test
    fun `re-asking after denial is allowed (different day identity per flood rule)`() {
        val firstId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(firstId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.denyAccess(firstId, memberId)

        val secondId = TestFixtures.uuid()
        val reAsk =
            ReliefAccessService.requestReliefAccess(
                secondId,
                branchId,
                TestFixtures.today.plusDays(1),
                reliefUserId,
            )
        trackRequest(secondId)

        assertEquals(ReliefAccessStatus.PENDING, reAsk.requestStatus)
        assertTrue(
            ReliefAccessRepository.findByRequestedByAndBranchDayId(
                reliefUserId,
                reAsk.branchDayId,
                ReliefAccessStatus.PENDING,
            ) !=
                null,
        )
    }

    @Test
    fun `re-asking after cancellation is allowed on a different day`() {
        val firstId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(firstId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.cancelRequest(firstId, reliefUserId)

        val secondId = TestFixtures.uuid()
        val reAsk =
            ReliefAccessService.requestReliefAccess(secondId, branchId, TestFixtures.today.plusDays(1), reliefUserId)
        trackRequest(secondId)

        assertEquals(ReliefAccessStatus.PENDING, reAsk.requestStatus)
    }

    // ──────────────────────────────────────────────
    // Discovery reads
    // ──────────────────────────────────────────────

    @Test
    fun `members see every request on the day, outsiders only their own`() {
        val otherOutsider = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherOutsider, "relief-3")
        val mine = TestFixtures.uuid()
        val theirs = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(mine, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.requestReliefAccess(theirs, branchId, TestFixtures.today, otherOutsider)

        val memberView = ReliefAccessService.listForCaller(memberId, branchDayId)
        val outsiderView = ReliefAccessService.listForCaller(reliefUserId, branchDayId)

        assertEquals(setOf(mine, theirs), memberView.map { it.id }.toSet())
        assertEquals(listOf(mine), outsiderView.map { it.id })
    }

    @Test
    fun `listForCaller with unknown branch day throws NotFoundException`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.listForCaller(memberId, TestFixtures.uuid())
        }
    }

    @Test
    fun `listForCaller on existing day with zero requests returns empty list`() {
        val emptyDayId = TestFixtures.uuid()
        insertBranchDay(emptyDayId, branchId, date = TestFixtures.today.plusDays(9))

        assertTrue(ReliefAccessService.listForCaller(memberId, emptyDayId).isEmpty())
    }

    @Test
    fun `deep-link read on missing day returns empty list without materializing`() {
        val missingDate = TestFixtures.today.plusDays(30)

        assertTrue(ReliefAccessService.listForCallerByDay(memberId, branchId, missingDate).isEmpty())
        assertTrue(BranchDayService.findByBranchAndDate(branchId, missingDate) == null)
    }

    @Test
    fun `deep-link read with unknown branch throws NotFoundException`() {
        assertFailsWith<NotFoundException> {
            ReliefAccessService.listForCallerByDay(memberId, TestFixtures.uuid(), TestFixtures.today)
        }
    }

    @Test
    fun `deep-link read on existing day with zero requests returns empty list`() {
        val emptyDate = TestFixtures.today.plusDays(11)
        insertBranchDay(TestFixtures.uuid(), branchId, date = emptyDate)

        assertTrue(ReliefAccessService.listForCallerByDay(memberId, branchId, emptyDate).isEmpty())
    }

    @Test
    fun `mine list carries branch context across days`() {
        val todayId = TestFixtures.uuid()
        val futureId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(todayId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.requestReliefAccess(futureId, branchId, TestFixtures.today.plusDays(1), reliefUserId)
        trackRequest(futureId)

        val mine = ReliefAccessRepository.findMine(reliefUserId)

        assertEquals(setOf(todayId, futureId), mine.map { it.access.id }.toSet())
        assertTrue(mine.all { it.branchName == branchName && it.date >= TestFixtures.today })
    }
}
