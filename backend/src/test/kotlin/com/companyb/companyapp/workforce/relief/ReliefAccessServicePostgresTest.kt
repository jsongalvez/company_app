package com.companyb.companyapp.workforce.relief
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #357 broadcast model: a request names no target user and is branch-day-scoped (today or
 * a future date); any ACTIVE outsider may raise it, any active branch member grants,
 * denies, or cancels it; all retraction locks once the requester clocks in. The #354
 * supersede machinery is retired — multiple relief workers per branch-day are allowed.
 *
 * Over the 600-line LargeClass threshold since #409's audience test; test-class precedent
 * (RemittanceServicePostgresTest, BranchInventoryServicePostgresTest).
 */
@Suppress("LargeClass") // #598 scenario coverage stays whole.
class ReliefAccessServicePostgresTest : BasePostgresTest() {
    private val reliefUserId = TestFixtures.uuid()
    private val memberId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchName = "ReliefTest-${branchId.toString().take(8)}"
    private val branchDayId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(reliefUserId, "relief")
        IdentityFixtures.insertTestUser(memberId, "member")
        BranchWorkforceFixtures.insertTestBranch(branchId, branchName)
        insertBranchDay(branchDayId, branchId)
        // The member's home assignment — grant/deny/cancel authority (#357).
        BranchWorkforceFixtures.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
        )
        // Member-authored grants/denies/cancels write audit rows too (#357 membership authority).
        // #358 — broadcast writes land in the notification table; every recipient's row
        // carries this branch id, so one column key tracks them all.
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
    // Grant / deny (membership authority, no targeting)
    // ──────────────────────────────────────────────

    @Test
    fun `grant by a branch member sets GRANTED, writes capability and audit`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.grantAccess(requestId, memberId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.GRANTED, result.requestStatus)
        assertEquals(memberId, result.grantedBy)
        assertNotNull(result.grantedAt)
        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = result.branchDayId,
            ),
        )
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `future-dated grant yields edit access scoped to that day only`() {
        val futureDate = TestFixtures.today.plusDays(FUTURE_DAYS)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, futureDate, reliefUserId)
        trackRequest(requestId)
        val futureDayId = ReliefAccessRepository.findById(requestId)!!.branchDayId

        ReliefAccessService.grantAccess(requestId, memberId)

        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = futureDayId,
            ),
        )
        // A different day gains nothing.
        assertFalse(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = branchDayId,
            ),
        )
    }

    @Test
    fun `grant by a non-member fails with 403`() {
        val outsider = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsider, "outsider")
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, outsider)
        }
    }

    @Test
    fun `grant replay on already granted request is idempotent`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(requestId, memberId)

        val duplicate = ReliefAccessService.grantAccess(requestId, memberId)

        assertEquals(requestId, duplicate.id)
        assertEquals(ReliefAccessStatus.GRANTED, duplicate.requestStatus)
        assertEquals(memberId, duplicate.grantedBy)
    }

    @Test
    fun `two requests from different requesters can both be granted (no supersede)`() {
        val secondRelief = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(secondRelief, "relief-2")
        val request1 = TestFixtures.uuid()
        val request2 = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(request1, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.requestReliefAccess(request2, branchId, TestFixtures.today, secondRelief)

        val grant1 = ReliefAccessService.grantAccess(request1, memberId)
        val grant2 = ReliefAccessService.grantAccess(request2, memberId)

        assertEquals(ReliefAccessStatus.GRANTED, grant1.requestStatus)
        assertEquals(ReliefAccessStatus.GRANTED, grant2.requestStatus)
        assertTrue(
            CapabilityService.hasCapability(
                userId = reliefUserId,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = grant1.branchDayId,
            ),
        )
        assertTrue(
            CapabilityService.hasCapability(
                userId = secondRelief,
                capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH_DAY,
                contextId = grant2.branchDayId,
            ),
        )
    }

    @Test
    fun `deny by a branch member sets DENIED and writes audit`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        val result = ReliefAccessService.denyAccess(requestId, memberId)

        assertEquals(requestId, result.id)
        assertEquals(ReliefAccessStatus.DENIED, result.requestStatus)
        assertEquals(2L, auditReliefEntryCount(requestId))
    }

    @Test
    fun `deny replay is idempotent and deny after grant fails with 400`() {
        val deniedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(deniedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.denyAccess(deniedId, memberId)
        assertEquals(ReliefAccessStatus.DENIED, ReliefAccessService.denyAccess(deniedId, memberId).requestStatus)

        val grantedId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(grantedId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(grantedId, memberId)
        assertFailsWith<ValidationException> {
            ReliefAccessService.denyAccess(grantedId, memberId)
        }
    }

    @Test
    fun `deny by a non-member fails with 403`() {
        val outsider = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsider, "other")
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, outsider)
        }
    }

    @Test
    fun `grant and deny on non-existent requests fail with 404`() {
        assertFailsWith<NotFoundException> { ReliefAccessService.grantAccess(TestFixtures.uuid(), memberId) }
        assertFailsWith<NotFoundException> { ReliefAccessService.denyAccess(TestFixtures.uuid(), memberId) }
    }

    @Test
    fun `grant on REMITTED day with reason succeeds and flags audit entry`() {
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq branchDayId }) {
                it[BranchDayTable.status] = DayStatus.REMITTED
            }
        }
        val requestId = TestFixtures.uuid()
        // Distinct-name local: inside insert{}, a bare name colliding with a table column
        // resolves to the COLUMN via the implicit receiver (the #356 lesson).
        val seededDayForRemit = branchDayId
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = seededDayForRemit
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }
        BranchWorkforceFixtures.grantEditPastDay(memberId, branchId, TestFixtures.uuid())

        val result = ReliefAccessService.grantAccess(requestId, memberId, "Coordinator correction")

        assertEquals(ReliefAccessStatus.GRANTED, result.requestStatus)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq GrantReliefAccessTable.tableName) and
                            (AuditLogTable.recordId eq requestId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `grant fails with 403 on REMITTED day without EDIT_PAST_DAY`() {
        val remittedDayId = TestFixtures.uuid()
        insertBranchDay(remittedDayId, branchId, DayStatus.REMITTED, TestFixtures.today.minusDays(1))
        val requestId = TestFixtures.uuid()
        transaction {
            GrantReliefAccessTable.insert {
                it[GrantReliefAccessTable.id] = requestId
                it[GrantReliefAccessTable.branchDayId] = remittedDayId
                it[GrantReliefAccessTable.requestedBy] = reliefUserId
                it[GrantReliefAccessTable.requestStatus] = ReliefAccessStatus.PENDING
            }
        }

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, memberId)
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

    private val inactiveRequester = TestFixtures.uuid()

    // ──────────────────────────────────────────────
    // #358 notification events (broadcast writes commit with the causing command)
    // ──────────────────────────────────────────────

    @Test
    fun `request creation broadcasts one message per member naming today`() {
        val requestId = TestFixtures.uuid()

        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        val notices = notificationsFor(memberId)
        assertEquals(1, notices.size)
        val notice = notices.single()
        assertEquals(ReliefNotifications.REQUESTED, notice.eventType)
        assertEquals(requestId, notice.sourceId)
        assertEquals(branchId, notice.branchId)
        assertEquals(TestFixtures.today, notice.targetDate)
        assertTrue(notice.message.contains("requested relief duty at $branchName for today"))
        assertTrue(notice.message.contains("relief"))
        // The requester raised it — no self-ping.
        assertTrue(notificationsFor(reliefUserId).isEmpty())
    }

    // #409 — deactivation revokes access but leaves the assignment open; the audience
    // definition joins user status, so an INACTIVE member never receives broadcast rows.
    @Test
    fun `broadcast skips deactivated members with open assignments`() {
        val inactiveMember = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(inactiveMember, "inactive-member")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = inactiveMember,
            branchId = branchId,
            slot = 3,
            assignedBy = memberId,
        )
        transaction {
            AppUserTable.update({ AppUserTable.id eq inactiveMember }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
        }

        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, null, reliefUserId)
        trackRequest(requestId)

        assertTrue(notificationsFor(memberId).any { it.eventType == ReliefNotifications.REQUESTED })
        assertTrue(notificationsFor(inactiveMember).isEmpty())
    }

    @Test
    fun `future-dated request copy names the date instead of today`() {
        val futureDate = TestFixtures.today.plusDays(FUTURE_DAYS)
        val requestId = TestFixtures.uuid()

        ReliefAccessService.requestReliefAccess(requestId, branchId, futureDate, reliefUserId)
        trackRequest(requestId)

        val notice = notificationsFor(memberId).single()
        assertTrue(notice.message.endsWith("on $futureDate"))
        assertEquals(futureDate, notice.targetDate)
    }

    @Test
    fun `grant broadcasts the outcome to members and the requester`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        ReliefAccessService.grantAccess(requestId, memberId)

        val memberNotices = notificationsFor(memberId).filter { it.eventType == ReliefNotifications.GRANTED }
        val requesterNotices = notificationsFor(reliefUserId).filter { it.eventType == ReliefNotifications.GRANTED }
        assertEquals(1, memberNotices.size)
        assertEquals(1, requesterNotices.size)
        val message = memberNotices.single().message
        assertTrue(message.contains("granted"))
        assertTrue(message.contains("member"))
        assertTrue(message.contains("relief"))
    }

    @Test
    fun `deny broadcasts the outcome to members and the requester`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        ReliefAccessService.denyAccess(requestId, memberId)

        val requesterNotice = notificationsFor(reliefUserId).single { it.eventType == ReliefNotifications.DENIED }
        assertTrue(requesterNotice.message.contains("denied"))
    }

    @Test
    fun `idempotent grant replay does not duplicate the outcome broadcast`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        trackRequest(requestId)

        ReliefAccessService.grantAccess(requestId, memberId)
        ReliefAccessService.grantAccess(requestId, memberId)

        assertEquals(
            1,
            notificationsFor(memberId).count { it.eventType == ReliefNotifications.GRANTED },
        )
    }

    @Test
    fun `expiry job announces past-day pending requests to the original ping list`() {
        // A live request whose day silently slides into the past: create for a future
        // date, then rewind the day row — requestReliefAccess itself rejects past dates.
        val expiredDate = TestFixtures.today.minusDays(1)
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(
            requestId,
            branchId,
            TestFixtures.today.plusDays(FUTURE_DAYS),
            reliefUserId,
        )
        trackRequest(requestId)
        transaction {
            BranchDayTable.update({ BranchDayTable.id eq ReliefAccessRepository.findById(requestId)!!.branchDayId }) {
                it[BranchDayTable.date] = expiredDate
            }
        }

        val announced = ReliefRequestExpiryJob.run()

        assertEquals(1, announced)
        val memberNotice = notificationsFor(memberId).single { it.eventType == ReliefNotifications.EXPIRED }
        assertEquals(requestId, memberNotice.sourceId)
        assertTrue(memberNotice.message.contains("expired unanswered"))

        // Idempotent: the stored notice is the marker — re-runs announce nothing.
        assertEquals(0, ReliefRequestExpiryJob.run())
    }

    @Test
    fun `invite acceptance broadcasts to the branch members`() {
        val inviteeId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(inviteeId, "relief-invitee")
        // The acceptance audit row is authored by the invitee; the capability by grant.

        // Mint through the service: the invite lands on the pre-seeded today day row.
        val invite = ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)

        ReliefInviteService.acceptInvite(inviteeId, invite.id)

        val notice = notificationsFor(memberId).single { it.eventType == ReliefNotifications.INVITE_ACCEPTED }
        assertEquals(invite.id, notice.sourceId)
        assertTrue(notice.message.contains("accepted the relief invite"))
        assertTrue(notice.message.contains("relief-invitee"))
    }

    /** Unread-or-read notification rows owned by [userId] (#358 broadcast assertions). */
    private fun notificationsFor(userId: UUID): List<com.companyb.companyapp.notification.Notification> =
        transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.userId eq userId }
                .map { row ->
                    com.companyb.companyapp.notification.Notification(
                        id = row[NotificationTable.id],
                        sessionId = row[NotificationTable.sessionId],
                        userId = row[NotificationTable.userId],
                        branchId = row[NotificationTable.branchId],
                        message = row[NotificationTable.message],
                        isRead = row[NotificationTable.isRead],
                        readAt = row[NotificationTable.readAt],
                        createdAt = row[NotificationTable.createdAt],
                        eventType = row[NotificationTable.eventType],
                        sourceId = row[NotificationTable.sourceId],
                        targetDate = row[NotificationTable.targetDate],
                    )
                }
        }

    /** Track a service-created request row and its (possibly fresh) branch day for teardown. */
    private fun trackRequest(requestId: UUID) {
        val dayId = ReliefAccessRepository.findById(requestId)?.branchDayId ?: return
    }

    private fun insertBranchDay(
        id: UUID,
        branchId: UUID,
        status: DayStatus = DayStatus.OPEN,
        date: LocalDate = TestFixtures.today,
    ) {
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
                it[BranchDayTable.status] = status
            }
        }
    }

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

    private fun requestExists(requestId: UUID): Boolean =
        transaction {
            GrantReliefAccessTable
                .selectAll()
                .where { GrantReliefAccessTable.id eq requestId }
                .empty()
                .not()
        }

    private fun auditReliefEntryCount(requestId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq GrantReliefAccessTable.tableName) and
                        (AuditLogTable.recordId eq requestId)
                }.count()
        }

    private companion object {
        const val FUTURE_DAYS = 2L
    }
}
