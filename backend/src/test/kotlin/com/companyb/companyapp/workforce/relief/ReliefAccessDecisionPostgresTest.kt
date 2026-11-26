package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Granting and denying a relief ask: member authority, replays and remitted days. */
class ReliefAccessDecisionPostgresTest : ReliefAccessServicePostgresBase() {
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
    fun `deny decided DENIED row by a non-member fails with 403`() {
        val outsider = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsider, "decided-outsider")
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.denyAccess(requestId, memberId)

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, outsider)
        }
    }

    @Test
    fun `deny decided GRANTED row by a non-member fails with 403`() {
        val outsider = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsider, "granted-outsider")
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
        ReliefAccessService.grantAccess(requestId, memberId)

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
        BranchWorkforceFixtures.grantEditPastDay(memberId, branchId)

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
}
