package com.companyb.companyapp.audit
import com.companyb.companyapp.audit.AuditLogStore
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuditLogServicePostgresTest : BasePostgresTest() {
    private val editorId = TestFixtures.uuid()
    private val acknowledgerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val recordId = TestFixtures.uuid()
    private val tableName = "test_table"

    override fun initTestData() {
        listOf(editorId to "audit-editor", acknowledgerId to "audit-acker").forEach { (id, prefix) ->
            IdentityFixtures.insertTestUser(id, prefix)
        }
        // Global VIEW_BRANCH_DATA = the NULL-branch read fallback (AuditLogReadScope).
        listOf(editorId, acknowledgerId).forEach {
            IdentityFixtures.grantCapability(
                userId = it,
                capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
                sourceId = sourceId,
            )
        }
    }

    @Test
    fun `findByTableAndRecord returns matching entries`() {
        insertAuditEntry(recordId, tableName, AuditAction.INSERT, false)

        val entries = AuditLogService.findByTableAndRecord(acknowledgerId, tableName, recordId)

        assertEquals(1, entries.size)
        assertEquals(tableName, entries[0].tableName)
        assertEquals(recordId, entries[0].recordId)
        assertEquals(AuditAction.INSERT, entries[0].action)
    }

    @Test
    fun `findByTableAndRecord returns empty list for no matches`() {
        val entries = AuditLogService.findByTableAndRecord(acknowledgerId, "non_existent", TestFixtures.uuid())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `findByTableAndRecord without any capability returns empty not error`() {
        insertAuditEntry(recordId, tableName, AuditAction.INSERT, false)
        val noGrantUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(noGrantUser, "audit-no-grant")

        // NULL-branch rows are invisible to a zero-grant caller, but the call
        // is allowed (D9: no route gate; scoping is authoritative, not 403).
        assertTrue(AuditLogService.findByTableAndRecord(noGrantUser, tableName, recordId).isEmpty())
    }

    @Test
    fun `findByTableAndRecord returns multiple entries ordered by changed_at desc`() {
        val entryId1 = TestFixtures.uuid()
        val entryId2 = TestFixtures.uuid()
        insertAuditEntryWithId(entryId1, recordId, tableName, AuditAction.INSERT, false)
        insertAuditEntryWithId(entryId2, recordId, tableName, AuditAction.UPDATE, false)

        val entries = AuditLogService.findByTableAndRecord(acknowledgerId, tableName, recordId)

        assertEquals(2, entries.size)
    }

    @Test
    fun `findFlagged returns only unacknowledged flagged entries`() {
        insertAuditEntry(TestFixtures.uuid(), "t1", AuditAction.UPDATE, true)
        insertAuditEntry(TestFixtures.uuid(), "t2", AuditAction.UPDATE, false)

        val entries = AuditLogService.findFlagged(acknowledgerId)

        assertTrue(entries.all { it.isFlagged })
        assertTrue(entries.all { it.acknowledgedAt == null })
    }

    @Test
    fun `findFlagged excludes acknowledged flagged entries`() {
        val entryId = TestFixtures.uuid()
        insertAuditEntryWithId(entryId, TestFixtures.uuid(), "t1", AuditAction.UPDATE, true)
        acknowledgeEntryDirectly(entryId)

        val entries = AuditLogService.findFlagged(acknowledgerId)
        assertTrue(entries.none { it.id == entryId })
    }

    @Test
    fun `findFlagged without any capability returns empty not error`() {
        insertAuditEntry(TestFixtures.uuid(), "t1", AuditAction.UPDATE, true)
        val noGrantUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(noGrantUser, "audit-no-grant-2")

        assertTrue(AuditLogService.findFlagged(noGrantUser).isEmpty())
    }

    @Test
    fun `acknowledge marks entry as acknowledged`() {
        val entryId = TestFixtures.uuid()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)

        val entry = AuditLogService.acknowledge(acknowledgerId, entryId)

        assertNotNull(entry.acknowledgedAt)
        assertEquals(acknowledgerId, entry.acknowledgedBy)
    }

    @Test
    fun `acknowledge outside the window returns not found`() {
        val entryId = TestFixtures.uuid()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)
        val noGrantUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(noGrantUser, "audit-no-grant-ack")

        // No 403: acknowledge is gated by the read window, not a capability
        // route gate (D9). Out-of-window rows are invisible -> 404.
        assertFailsWith<NotFoundException> {
            AuditLogService.acknowledge(noGrantUser, entryId)
        }
    }

    @Test
    fun `self-acknowledge throws conflict`() {
        val entryId = TestFixtures.uuid()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)

        assertFailsWith<ConflictException> {
            AuditLogService.acknowledge(editorId, entryId)
        }
    }

    @Test
    fun `acknowledge on non-existent entry returns not found`() {
        assertFailsWith<NotFoundException> {
            AuditLogService.acknowledge(acknowledgerId, TestFixtures.uuid())
        }
    }

    @Test
    fun `acknowledge on already acknowledged entry returns not found`() {
        val entryId = TestFixtures.uuid()
        insertAuditEntryWithId(entryId, recordId, tableName, AuditAction.UPDATE, true)
        acknowledgeEntryDirectly(entryId)

        assertFailsWith<NotFoundException> {
            AuditLogService.acknowledge(acknowledgerId, entryId)
        }
    }

    @Test
    fun `findByTableAndRecord hides other-branch rows from the caller window`() {
        val branchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(branchId, "Service Test Branch $branchId")
        val recId = recordId
        val tblName = tableName
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.recordId] = recId
                it[AuditLogTable.auditTableName] = tblName
                it[AuditLogTable.action] = AuditAction.INSERT
                it[AuditLogTable.changedBy] = editorId
                it[AuditLogTable.branchId] = branchId
            }
        }

        // Global VIEW_BRANCH_DATA holder reads across all branches (window = all).
        assertEquals(1, AuditLogService.findByTableAndRecord(acknowledgerId, tblName, recId).size)
    }

    @Test
    fun `findByTableAndRecord hides branch rows when a window is set`() {
        val branchA = TestFixtures.uuid()
        val branchB = TestFixtures.uuid()
        listOf(branchA to "Svc Branch A", branchB to "Svc Branch B").forEach { (id, name) ->
            BranchWorkforceFixtures.insertTestBranch(id, "$name $id")
        }
        // Branch-scoped editor without the global view grant: window = branchA only.
        val windowedUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(windowedUser, "audit-windowed")
        IdentityFixtures.grantCapability(
            userId = windowedUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchA,
            sourceId = sourceId,
        )
        val recId = recordId
        val tblName = tableName
        transaction {
            AuditLogTable.insert {
                it[AuditLogTable.recordId] = recId
                it[AuditLogTable.auditTableName] = tblName
                it[AuditLogTable.action] = AuditAction.INSERT
                it[AuditLogTable.changedBy] = editorId
                it[AuditLogTable.branchId] = branchB
            }
        }

        assertTrue(AuditLogService.findByTableAndRecord(windowedUser, tblName, recId).isEmpty())
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
                it[AuditLogTable.changedBy] = editorId
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
                it[AuditLogTable.changedBy] = editorId
                it[AuditLogTable.isFlagged] = isFlagged
            }
        }
    }

    private fun acknowledgeEntryDirectly(entryId: UUID) {
        AuditLogStore.acknowledge(entryId, acknowledgerId)
    }
}
