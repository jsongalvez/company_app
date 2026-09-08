package com.companyb.companyapp.session

import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.ProductSaleService
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #667 — void/unvoid commands join the commission recalculation in their transaction:
 * a voided session drops out of the aggregation input, so the persisted splits must
 * move on void and restore on unvoid. PAST/REMITTED auto-skip rides the shared
 * force=false guard (pinned by the parity past-day test).
 */
class SessionVoidCommissionPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val earnerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "void-commission-caller")
        IdentityFixtures.insertTestUser(earnerId, "void-commission-earner")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Void Commission Branch")
        SessionClientFixtures.insertTestClient(clientId)
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        ensureInventoryCard(branchId, productId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        clockIn(branchDayId, earnerId)
    }

    @Test
    fun `void with linked sale clears persisted splits and unvoid restores them`() {
        ProductSaleService.sell(
            callerId = callerId,
            id = TestFixtures.uuid(),
            branchDayId = branchDayId,
            sessionId = sessionId,
            // sale_type_logic: a session-linked sale carries client_id NULL + walk-in FALSE.
            clientId = null,
            isWalkIn = false,
            productId = productId,
            quantity = 1,
            expectedVersion = 1,
        )

        val before = CommissionService.getByBranchDayId(branchDayId)
        assertEquals(1, before.size)
        assertEquals(earnerId, before.single().userId)
        assertEquals(BigDecimal("10.0000"), before.single().amount)

        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")
        assertTrue(CommissionService.getByBranchDayId(branchDayId).isEmpty())

        SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        val after = CommissionService.getByBranchDayId(branchDayId)
        assertEquals(1, after.size)
        assertEquals(earnerId, after.single().userId)
        assertEquals(BigDecimal("10.0000"), after.single().amount)
    }

    private fun ensureInventoryCard(
        branchId: UUID,
        productId: UUID,
    ) {
        transaction {
            BranchInventoryTable.insertIgnore {
                it[BranchInventoryTable.branchId] = branchId
                it[BranchInventoryTable.productId] = productId
                it[BranchInventoryTable.currentStock] = 100
                it[BranchInventoryTable.version] = 1
            }
        }
    }

    private fun clockIn(
        dayId: UUID,
        userId: UUID,
    ) {
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = TestFixtures.uuid()
                it[AttendanceTable.branchDayId] = dayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = userId
                it[AttendanceTable.clockIn] = TestFixtures.realNow().atOffset(ZoneOffset.UTC).minusMinutes(30)
            }
        }
    }
}
