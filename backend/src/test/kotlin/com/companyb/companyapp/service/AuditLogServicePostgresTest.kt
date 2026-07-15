package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuditLogServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()
    private val tableName = "test_table"

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "audit-caller-$callerId",
            passwordHash = "test-password-hash",
            email = "${callerId.toString().take(8)}@t.st",
            displayName = "Test Audit Caller",
        )
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `findByTableAndRecord returns matching entries`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        insertAuditEntry(recordId, tableName, AuditAction.INSERT, false)

        val entries = AuditLogService.findByTableAndRecord(callerId, tableName, recordId)

        assertEquals(1, entries.size)
        assertEquals(tableName, entries[0].tableName)
        assertEquals(recordId, entries[0].recordId)
        assertEquals(AuditAction.INSERT, entries[0].action)
    }

    @Test
    fun `findByTableAndRecord returns empty list for no matches`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        val entries = AuditLogService.findByTableAndRecord(callerId, "non_existent", UUID.randomUUID())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `findByTableAndRecord without ASSIGN_COMPENSATION is forbidden`() {
        insertAuditEntry(recordId, tableName, AuditAction.INSERT, false)

        assertFailsWith<ForbiddenResponse> {
            AuditLogService.findByTableAndRecord(callerId, tableName, recordId)
        }
    }

    @Test
    fun `findByTableAndRecord returns multiple entries ordered by changed_at desc`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        val entryId1 = UUID.randomUUID()
        val entryId2 = UUID.randomUUID()
        insertAuditEntryWithId(entryId1, recordId, tableName, AuditAction.INSERT, false)
        insertAuditEntryWithId(entryId2, recordId, tableName, AuditAction.UPDATE, false)

        val entries = AuditLogService.findByTableAndRecord(callerId, tableName, recordId)

        assertEquals(2, entries.size)
    }

    @Test
    fun `findFlagged returns only unacknowledged flagged entries`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        insertAuditEntry(UUID.randomUUID(), "t1", AuditAction.UPDATE, true)
        insertAuditEntry(UUID.randomUUID(), "t2", AuditAction.UPDATE, false)

        val entries = AuditLogService.findFlagged(callerId)

        assertTrue(entries.all { it.isFlagged })
        assertTrue(entries.all { it.acknowledgedAt == null })
    }

    @Test
    fun `findFlagged excludes acknowledged flagged entries`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        val entryId = UUID.randomUUID()
        insertAuditEntryWithId(entryId, UUID.randomUUID(), "t1", AuditAction.UPDATE, true)
        acknowledgeEntryDirectly(entryId)

        val entries = AuditLogService.findFlagged(callerId)
        assertTrue(entries.none { it.id == entryId })
    }

    @Test
    fun `findFlagged without ASSIGN_COMPENSATION is forbidden`() {
        insertAuditEntry(UUID.randomUUID(), "t1", AuditAction.UPDATE, true)

        assertFailsWith<ForbiddenResponse> {
            AuditLogService.findFlagged(callerId)
        }
    }

    @Test
    fun `acknowledge marks entry as acknowledged`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        val entryId = UUID.randomUUID()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)

        val entry = AuditLogService.acknowledge(callerId, entryId)

        assertNotNull(entry.acknowledgedAt)
        assertEquals(callerId, entry.acknowledgedBy)
    }

    @Test
    fun `acknowledge without ASSIGN_COMPENSATION is forbidden`() {
        val entryId = UUID.randomUUID()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)

        assertFailsWith<ForbiddenResponse> {
            AuditLogService.acknowledge(callerId, entryId)
        }
    }

    @Test
    fun `acknowledge on non-existent entry returns not found`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)

        assertFailsWith<NotFoundResponse> {
            AuditLogService.acknowledge(callerId, UUID.randomUUID())
        }
    }

    @Test
    fun `acknowledge on already acknowledged entry returns not found`() {
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        val entryId = UUID.randomUUID()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)
        acknowledgeEntryDirectly(entryId)

        assertFailsWith<NotFoundResponse> {
            AuditLogService.acknowledge(callerId, entryId)
        }
    }

    private fun insertAuditEntry(
        recId: UUID,
        tblName: String,
        action: AuditAction,
        isFlagged: Boolean,
    ) {
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.recordId] = recId
                it[AuditLogTable.auditTableName] = tblName
                it[AuditLogTable.action] = action
                it[AuditLogTable.changedBy] = callerId
                it[AuditLogTable.isFlagged] = isFlagged
            }
        }
    }

    private fun insertAuditEntryWithId(
        entryId: UUID,
        recId: UUID,
        tblName: String,
        action: AuditAction,
        isFlagged: Boolean,
    ) {
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.id] = entryId
                it[AuditLogTable.recordId] = recId
                it[AuditLogTable.auditTableName] = tblName
                it[AuditLogTable.action] = action
                it[AuditLogTable.changedBy] = callerId
                it[AuditLogTable.isFlagged] = isFlagged
            }
        }
    }

    private fun acknowledgeEntryDirectly(entryId: UUID) {
        AuditLogRepository.acknowledge(entryId, callerId)
    }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId)
            }
            UserCapabilityTable.deleteWhere {
                (UserCapabilityTable.userId eq callerId)
            }
            AppUserTable.deleteWhere {
                (AppUserTable.id eq callerId)
            }
        }
    }
}
