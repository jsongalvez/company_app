package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemittanceLineDeleteAndTotalsPostgresTest : RemittanceLineServicePostgresBase() {
    @Test
    fun `delete line succeeds and increments version`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )

        val afterAdd = requireNotNull(RemittanceService.getRemittance(remittance.id).remittance)

        val deleted = RemittanceService.removeLine(callerId, remittance.id, lineId)
        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)

        val afterDelete = RemittanceService.getRemittance(remittance.id).remittance
        assertEquals(afterAdd.version + 1, afterDelete.version)
    }

    @Test
    fun `delete non-existent line returns not found`() {
        val remittance = createDraftRemittance()

        assertFailsWith<NotFoundException> {
            RemittanceService.removeLine(callerId, remittance.id, TestFixtures.uuid())
        }
    }

    @Test
    fun `delete line through another remittance returns not found without mutation`() {
        val owner = createDraftRemittance()
        val other = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = owner.id,
            sessionLine(lineId),
        )

        val ownerVersionBefore = RemittanceService.getRemittance(owner.id).remittance.version
        val otherVersionBefore = RemittanceService.getRemittance(other.id).remittance.version

        assertFailsWith<NotFoundException> {
            RemittanceService.removeLine(callerId, other.id, lineId)
        }

        val ownerDetail = RemittanceService.getRemittance(owner.id)
        assertEquals(1, ownerDetail.lines.size)
        assertEquals(BigDecimal("1500.00"), ownerDetail.totalAmount)
        assertEquals(ownerVersionBefore, ownerDetail.remittance.version)
        assertEquals(otherVersionBefore, RemittanceService.getRemittance(other.id).remittance.version)
    }

    @Test
    fun `delete line from non-DRAFT remittance returns bad request`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )

        markRemittanceSubmitted(remittance.id)

        assertFailsWith<ValidationException> {
            RemittanceService.removeLine(callerId, remittance.id, lineId)
        }
    }

    @Test
    fun `delete line without SUBMIT_REMITTANCE is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )

        val deleted = RemittanceService.removeLine(otherUser, remittance.id, lineId)
        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)
    }

    @Test
    fun `get remittance detail returns lines and day breakdowns`() {
        val remittance = createDraftRemittance()
        createSession()
        createProductSale()

        val lineId = TestFixtures.uuid()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )

        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittance.id,
            id = TestFixtures.uuid(),
            branchDayId = branchDayId,
        )

        val detail = RemittanceService.getRemittance(remittance.id)
        assertEquals(remittance.id, detail.remittance.id)
        assertEquals(1, detail.lines.size)
        assertEquals(1, detail.dayBreakdowns.size)
        assertEquals(BigDecimal("1500.00"), detail.totalAmount)
    }

    @Test
    fun `get remittance with multiple lines calculates total correctly`() {
        val remittance = createDraftRemittance()
        createSession()
        completeSession(sessionId)
        val sessionId2 = TestFixtures.uuid()
        createSession(sessionId2)
        val lineId1 = TestFixtures.uuid()
        val lineId2 = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId1, BigDecimal("1000.00")),
        )

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            NewRemittanceLine(
                id = lineId2,
                type = RemittanceLineType.SESSION,
                sessionId = sessionId2,
                productSaleId = null,
                amount = BigDecimal("2000.00"),
            ),
        )

        val detail = RemittanceService.getRemittance(remittance.id)
        assertEquals(BigDecimal("3000.00"), detail.totalAmount)
    }

    @Test
    fun `deleted line is excluded from totals`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId, BigDecimal("1000.00")),
        )

        RemittanceService.removeLine(callerId, remittance.id, lineId)

        val detail = RemittanceService.getRemittance(remittance.id)
        assertEquals(0, detail.lines.size)
        assertEquals(BigDecimal.ZERO, detail.totalAmount)
    }

    @Test
    fun `get remittance without capability is allowed at service layer`() {
        val otherUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherUser, "rl")
        val remittance = createDraftRemittance()

        val detail = RemittanceService.getRemittance(remittance.id)

        assertEquals(remittance.id, detail.remittance.id)
        assertTrue(detail.lines.isEmpty())
    }

    @Test
    fun `get non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.getRemittance(TestFixtures.uuid())
        }
    }

    @Test
    fun `add line writes audit log entry`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq "remittance_line")
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `delete line writes audit log entry`() {
        val remittance = createDraftRemittance()
        createSession()
        val lineId = TestFixtures.uuid()

        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittance.id,
            sessionLine(lineId),
        )

        RemittanceService.removeLine(callerId, remittance.id, lineId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq "remittance_line") and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }
}
