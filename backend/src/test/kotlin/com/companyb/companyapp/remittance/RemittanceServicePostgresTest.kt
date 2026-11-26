package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemittanceServicePostgresTest : RemittanceServicePostgresBase() {
    @Test
    fun `create draft remittance succeeds`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                draft(remittanceId, dateRangeStart, dateRangeEnd),
            )

        assertNotNull(remittance)
        assertEquals(remittanceId, remittance.id)
        assertEquals(RemittanceType.SESSION, remittance.type)
        assertEquals(RemittanceStatus.DRAFT, remittance.status)
        assertEquals(branchId, remittance.branchId)
        assertEquals(RemittanceMethod.BANK_TRANSFER, remittance.method)
        assertEquals(dateRangeStart, remittance.dateRangeStart)
        assertEquals(dateRangeEnd, remittance.dateRangeEnd)
        assertEquals(callerId, remittance.submittedBy)
        assertEquals(1, remittance.version)
    }

    @Test
    fun `create draft PRODUCT type succeeds`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                draft(
                    remittanceId,
                    dateRangeStart,
                    dateRangeEnd,
                    RemittanceType.PRODUCT,
                    RemittanceMethod.HANDED_TO_ACCOUNTANT,
                ),
            )

        assertEquals(RemittanceType.PRODUCT, remittance.type)
    }

    @Test
    fun `create draft idempotent duplicate returns existing`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val first =
            RemittanceService.createDraft(
                callerId = callerId,
                draft(remittanceId, dateRangeStart, dateRangeEnd),
            )

        val second =
            RemittanceService.createDraft(
                callerId = callerId,
                draft(remittanceId, dateRangeStart, dateRangeEnd),
            )

        assertEquals(first.id, second.id)
        assertEquals(first.version, second.version)
    }

    @Test
    fun `create draft rejects same id with divergent type`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        RemittanceService.createDraft(
            callerId = callerId,
            draft(remittanceId, dateRangeStart, dateRangeEnd),
        )

        // #927 — same-UUID divergent header must 409, never ack the stale row (#922 class).
        assertFailsWith<ConflictException> {
            RemittanceService.createDraft(
                callerId = callerId,
                draft(remittanceId, dateRangeStart, dateRangeEnd, RemittanceType.PRODUCT),
            )
        }
    }

    @Test
    fun `create draft rejects same id with divergent method and range`() {
        val remittanceId = TestFixtures.uuid()

        RemittanceService.createDraft(
            callerId = callerId,
            draft(remittanceId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
        )

        // #927 — method divergence alone 409s.
        assertFailsWith<ConflictException> {
            RemittanceService.createDraft(
                callerId = callerId,
                draft(
                    remittanceId,
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 15),
                    method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
                ),
            )
        }

        // #927 — range divergence alone 409s.
        assertFailsWith<ConflictException> {
            RemittanceService.createDraft(
                callerId = callerId,
                draft(remittanceId, LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 16)),
            )
        }
    }

    @Test
    fun `create draft rejects same id with different caller`() {
        val remittanceId = TestFixtures.uuid()
        val otherCallerId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCallerId, "remittance-other-caller")

        RemittanceService.createDraft(
            callerId = callerId,
            draft(remittanceId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
        )

        assertFailsWith<ConflictException> {
            RemittanceService.createDraft(
                callerId = otherCallerId,
                draft(remittanceId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
            )
        }
    }

    @Test
    fun `create draft without SUBMIT_REMITTANCE is allowed at service layer`() {
        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                draft(TestFixtures.uuid(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
            )

        assertNotNull(remittance)
    }

    @Test
    fun `create draft with non-existent branch returns not found`() {
        val nonexistentBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            RemittanceService.createDraft(
                callerId = callerId,
                NewRemittance(
                    id = TestFixtures.uuid(),
                    type = RemittanceType.SESSION,
                    branchId = nonexistentBranchId,
                    method = RemittanceMethod.BANK_TRANSFER,
                    dateRangeStart = LocalDate.of(2026, 7, 1),
                    dateRangeEnd = LocalDate.of(2026, 7, 15),
                ),
            )
        }
    }

    @Test
    fun `create draft writes audit log entry`() {
        val remittanceId = TestFixtures.uuid()

        RemittanceService.createDraft(
            callerId = callerId,
            draft(remittanceId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq RemittanceTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }
}
