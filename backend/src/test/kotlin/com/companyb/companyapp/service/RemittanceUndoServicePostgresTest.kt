package com.companyb.companyapp.service
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.service.finance.remittance.RemittanceFinancialSnapshotRepository
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.select
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemittanceUndoServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private val dayDate = LocalDate.of(2026, 7, 10)

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "remittance-undo-caller")

        DatabaseTestHelper.insertTestBranch(branchId, "Undo Branch ${TestFixtures.uuid()}")

        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
    }

    @Test
    fun `undo submitted SESSION remittance returns to DRAFT deletes snapshot and unlocks days`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        val dayId = addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)
        assertSnapshot(remittanceId, exists = true)
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayId))

        val undone = RemittanceService.undo(callerId, remittanceId, submittedVersion, "wrong day selected")

        assertEquals(RemittanceStatus.DRAFT, undone.status)
        assertEquals(submittedVersion + 1, undone.version)
        assertNull(undone.submittedAt)
        assertSnapshot(remittanceId, exists = false)
        val expectedStatus =
            BranchDayService.evaluateStatus(DayStatus.OPEN, dayDate, TestFixtures.today)
        assertEquals(expectedStatus, dbDayStatus(dayId))
    }

    @Test
    fun `undo writes audit rows for remittance days and snapshot with the reason`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        val dayId = addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)

        RemittanceService.undo(callerId, remittanceId, submittedVersion, "wrong branch day included")

        val remittanceUpdate = auditRow(AuditAction.UPDATE, RemittanceTable.tableName, remittanceId)
        assertNotNull(remittanceUpdate)
        assertEquals("wrong branch day included", remittanceUpdate[AuditLogTable.reason])
        assertTrue(remittanceUpdate[AuditLogTable.oldValue].orEmpty().contains("\"status\": \"SUBMITTED\""))
        assertTrue(remittanceUpdate[AuditLogTable.newValue].orEmpty().contains("\"status\": \"DRAFT\""))

        val snapshotDelete = auditRow(AuditAction.DELETE, RemittanceFinancialSnapshotTable.tableName, remittanceId)
        assertNotNull(snapshotDelete)
        assertEquals("wrong branch day included", snapshotDelete[AuditLogTable.reason])
        assertTrue(snapshotDelete[AuditLogTable.oldValue].orEmpty().contains("\"netIncome\""))

        val dayUpdate = auditRow(AuditAction.UPDATE, BranchDayTable.tableName, dayId)
        assertNotNull(dayUpdate)
        assertEquals("wrong branch day included", dayUpdate[AuditLogTable.reason])
    }

    @Test
    fun `undo submitted PRODUCT remittance succeeds without snapshot`() {
        val remittanceId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)
        assertSnapshot(remittanceId, exists = false)

        val undone = RemittanceService.undo(callerId, remittanceId, submittedVersion, "wrong type")

        assertEquals(RemittanceStatus.DRAFT, undone.status)
        assertEquals(RemittanceType.PRODUCT, undone.type)
    }

    @Test
    fun `undo within the 48h boundary succeeds and past it throws`() {
        val withinWindowId = createDraft(RemittanceType.SESSION)
        addBreakdown(withinWindowId)
        val withinVersion = submit(withinWindowId)
        val withinSubmittedAt = RemittanceService.getRemittance(withinWindowId).remittance.submittedAt

        val undone =
            RemittanceService.undoAt(
                callerId = callerId,
                remittanceId = withinWindowId,
                expectedVersion = withinVersion,
                reason = "boundary",
                now = withinSubmittedAt!!.plusHours(WINDOW_HOURS),
            )
        assertEquals(RemittanceStatus.DRAFT, undone.status)

        val expiredId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(expiredId)
        val expiredVersion = submit(expiredId)
        val expiredSubmittedAt = RemittanceService.getRemittance(expiredId).remittance.submittedAt

        assertFailsWith<ValidationException> {
            RemittanceService.undoAt(
                callerId = callerId,
                remittanceId = expiredId,
                expectedVersion = expiredVersion,
                reason = "late",
                now = expiredSubmittedAt!!.plusHours(WINDOW_HOURS).plusSeconds(1),
            )
        }
        assertEquals(RemittanceStatus.SUBMITTED, RemittanceService.getRemittance(expiredId).remittance.status)
    }

    @Test
    fun `production undo uses database time for expiry`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)
        val databaseNow =
            transaction {
                RemittanceTable
                    .select(CurrentTimestampWithTimeZone)
                    .first()[CurrentTimestampWithTimeZone]
            }
        backdateSubmittedAt(remittanceId, databaseNow.minusHours(WINDOW_HOURS).plusSeconds(1))

        val undone = RemittanceService.undo(callerId, remittanceId, submittedVersion, "database clock")

        assertEquals(RemittanceStatus.DRAFT, undone.status)
    }

    @Test
    fun `undo after window throws validation and does not mutate`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)

        backdateSubmittedAt(
            remittanceId,
            TestFixtures
                .realNow()
                .atOffset(ZoneOffset.UTC)
                .minusHours(WINDOW_HOURS + 1),
        )

        val auditsBefore = callerAuditCount()

        assertFailsWith<ValidationException> {
            RemittanceService.undo(callerId, remittanceId, submittedVersion, "too late")
        }
        assertEquals(RemittanceStatus.SUBMITTED, RemittanceService.getRemittance(remittanceId).remittance.status)
        assertSnapshot(remittanceId, exists = true)
        assertEquals(auditsBefore, callerAuditCount(), "expired undo writes no audit rows")
    }

    @Test
    fun `undo with neither submitted_at nor snapshot throws validation`() {
        val remittanceId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)
        clearSubmittedAt(remittanceId)

        assertFailsWith<ValidationException> {
            RemittanceService.undo(callerId, remittanceId, submittedVersion, "no timestamp")
        }
    }

    @Test
    fun `undo falls back to snapshot snapshotted_at when submitted_at missing`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)
        clearSubmittedAt(remittanceId)

        val undone = RemittanceService.undo(callerId, remittanceId, submittedVersion, "fallback")

        assertEquals(RemittanceStatus.DRAFT, undone.status)
    }

    @Test
    fun `snapshot immutability trigger still blocks delete while remittance is SUBMITTED`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        submit(remittanceId)
        assertSnapshot(remittanceId, exists = true)

        assertFailsWith<org.jetbrains.exposed.v1.exceptions.ExposedSQLException> {
            transaction {
                RemittanceFinancialSnapshotRepository.deleteByRemittanceIdInTransaction(remittanceId)
            }
        }
        assertSnapshot(remittanceId, exists = true)
    }

    @Test
    fun `undo non-submitted remittance throws validation`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)

        val auditsBefore = callerAuditCount()

        assertFailsWith<ValidationException> {
            RemittanceService.undo(callerId, remittanceId, 1, "nope")
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected undo writes no audit rows")
    }

    @Test
    fun `undo missing remittance throws not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.undo(callerId, TestFixtures.uuid(), 1, "nope")
        }
    }

    @Test
    fun `undo with version mismatch throws conflict and does not mutate`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        submit(remittanceId)

        val auditsBefore = callerAuditCount()

        assertFailsWith<VersionMismatchException> {
            RemittanceService.undo(callerId, remittanceId, 1, "wrong version")
        }
        assertEquals(RemittanceStatus.SUBMITTED, RemittanceService.getRemittance(remittanceId).remittance.status)
        assertSnapshot(remittanceId, exists = true)
        assertEquals(auditsBefore, callerAuditCount(), "conflicted undo writes no audit rows")
    }

    @Test
    fun `undo then resubmit succeeds without unique-tuple swallow`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        val submittedVersion = submit(remittanceId)
        val submittedDateBeforeUndo = RemittanceService.getRemittance(remittanceId).remittance.submittedDate

        val undone = RemittanceService.undo(callerId, remittanceId, submittedVersion, "fixing line amounts")
        assertEquals(RemittanceStatus.DRAFT, undone.status)
        assertEquals(submittedDateBeforeUndo, undone.submittedDate)
        assertNull(undone.submittedAt)
        assertSnapshot(remittanceId, exists = false)

        val resubmitted = RemittanceService.submit(callerId, remittanceId, undone.version)

        assertNotNull(resubmitted)
        assertEquals(RemittanceStatus.SUBMITTED, resubmitted.remittance.status)
        assertSnapshot(remittanceId, exists = true)
    }

    @Test
    fun `update header succeeds with version bump and audit`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        val newRangeStart = LocalDate.of(2026, 7, 5)
        val newRangeEnd = LocalDate.of(2026, 7, 20)

        val updated =
            RemittanceService.updateHeader(
                callerId = callerId,
                remittanceId = remittanceId,
                type = RemittanceType.PRODUCT,
                method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
                dateRangeStart = newRangeStart,
                dateRangeEnd = newRangeEnd,
                expectedVersion = 1,
            )

        assertEquals(RemittanceType.PRODUCT, updated.type)
        assertEquals(RemittanceMethod.HANDED_TO_ACCOUNTANT, updated.method)
        assertEquals(newRangeStart, updated.dateRangeStart)
        assertEquals(newRangeEnd, updated.dateRangeEnd)
        assertEquals(2, updated.version)

        val audit = auditRow(AuditAction.UPDATE, RemittanceTable.tableName, remittanceId)
        assertNotNull(audit)
        assertTrue(audit[AuditLogTable.oldValue].orEmpty().contains("\"type\": \"SESSION\""))
        assertTrue(audit[AuditLogTable.newValue].orEmpty().contains("\"type\": \"PRODUCT\""))
    }

    @Test
    fun `update header on submitted remittance throws validation`() {
        val remittanceId = createDraft(RemittanceType.SESSION)
        addBreakdown(remittanceId)
        submit(remittanceId)

        val auditsBefore = callerAuditCount()

        assertFailsWith<ValidationException> {
            RemittanceService.updateHeader(
                callerId = callerId,
                remittanceId = remittanceId,
                type = RemittanceType.PRODUCT,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
                expectedVersion = 99,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected header update writes no audit rows")
    }

    @Test
    fun `update header with version mismatch throws conflict`() {
        val remittanceId = createDraft(RemittanceType.SESSION)

        val auditsBefore = callerAuditCount()

        assertFailsWith<VersionMismatchException> {
            RemittanceService.updateHeader(
                callerId = callerId,
                remittanceId = remittanceId,
                type = RemittanceType.PRODUCT,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
                expectedVersion = 99,
            )
        }
        assertEquals(RemittanceType.SESSION, RemittanceService.getRemittance(remittanceId).remittance.type)
        assertEquals(auditsBefore, callerAuditCount(), "conflicted header update writes no audit rows")
    }

    @Test
    fun `update header to a type already used for the submitted date is allowed for drafts`() {
        val firstId = createDraft(RemittanceType.SESSION)
        addBreakdown(firstId)
        val secondId = createDraft(RemittanceType.PRODUCT)

        RemittanceService.updateHeader(
            callerId = callerId,
            remittanceId = secondId,
            type = RemittanceType.SESSION,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
            expectedVersion = 1,
        )
        assertEquals(RemittanceType.SESSION, RemittanceService.getRemittance(secondId).remittance.type)
    }

    @Test
    fun `update header on missing remittance throws not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.updateHeader(
                callerId = callerId,
                remittanceId = TestFixtures.uuid(),
                type = RemittanceType.PRODUCT,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
                expectedVersion = 1,
            )
        }
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private fun createDraft(type: RemittanceType): UUID {
        val id = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = id,
            type = type,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
        )
        return id
    }

    private fun addBreakdown(remittanceId: UUID): UUID {
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayId)
        return dayId
    }

    private fun submit(remittanceId: UUID): Int {
        val currentVersion = RemittanceService.getRemittance(remittanceId).remittance.version
        val result = RemittanceService.submit(callerId, remittanceId, currentVersion)
        assertNotNull(result)
        return result.remittance.version
    }

    private fun assertSnapshot(
        remittanceId: UUID,
        exists: Boolean,
    ) {
        val snapshot =
            transaction {
                RemittanceFinancialSnapshotTable
                    .selectAll()
                    .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                    .singleOrNull()
            }
        if (exists) {
            assertNotNull(snapshot)
        } else {
            assertNull(snapshot)
        }
    }

    private fun dbDayStatus(dayId: UUID): DayStatus =
        transaction {
            BranchDayTable
                .selectAll()
                .where { BranchDayTable.id eq dayId }
                .single()[BranchDayTable.status]
        }

    private fun callerAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.changedBy eq callerId }
                .count()
        }

    private fun auditRow(
        action: AuditAction,
        tableName: String,
        recordId: UUID,
    ): org.jetbrains.exposed.v1.core.ResultRow? =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.action eq action) and
                        (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId)
                }.orderBy(
                    AuditLogTable.changedAt to org.jetbrains.exposed.v1.core.SortOrder.DESC,
                    AuditLogTable.id to org.jetbrains.exposed.v1.core.SortOrder.DESC,
                ).firstOrNull()
        }

    private fun backdateSubmittedAt(
        remittanceId: UUID,
        instant: OffsetDateTime,
    ) {
        transaction {
            RemittanceTable.update({ RemittanceTable.id eq remittanceId }) {
                it[RemittanceTable.submittedAt] = instant
            }
        }
    }

    private fun clearSubmittedAt(remittanceId: UUID) {
        transaction {
            RemittanceTable.update({ RemittanceTable.id eq remittanceId }) {
                it[RemittanceTable.submittedAt] = null
            }
        }
    }

    private companion object {
        const val WINDOW_HOURS = 48L
    }
}
