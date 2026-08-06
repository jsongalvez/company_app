package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuditLogRepositoryPostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()
    private val tableName = "test_table"

    override fun initTestData() {
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "audit-repo-caller-$callerId",
            passwordHash = "test-password-hash",
            email = "${callerId.toString().take(8)}@t.st",
            displayName = "Test Audit Repo Caller",
        )
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    private fun findAuditRows(): List<ResultRow> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId)
                }.toList()
        }

    @Test
    fun `record inside transaction writes row`() {
        transaction {
            AuditLogRepository.record(
                tableName = tableName,
                recordId = recordId,
                action = AuditAction.INSERT,
                changedBy = callerId,
                newValue = """{"status":"active"}""",
            )
        }

        val rows = findAuditRows()
        assertEquals(1, rows.size)
        assertEquals(AuditAction.INSERT.name, rows[0][AuditLogTable.action].name)
        assertEquals(callerId, rows[0][AuditLogTable.changedBy])
    }

    @Test
    fun `record outside transaction throws`() {
        assertFailsWith<Exception> {
            AuditLogRepository.record(
                tableName = tableName,
                recordId = recordId,
                action = AuditAction.INSERT,
                changedBy = callerId,
            )
        }
    }

    @Test
    fun `recordInsert writes correct audit row`() {
        transaction {
            AuditLogRepository.recordInsert(
                tableName = tableName,
                recordId = recordId,
                changedBy = callerId,
                fields =
                    mapOf(
                        "id" to recordId.toString(),
                        "name" to "Alice",
                        "age" to "30",
                    ),
            )
        }

        val rows = findAuditRows()
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(AuditAction.INSERT.name, row[AuditLogTable.action].name)
        assertEquals(callerId, row[AuditLogTable.changedBy])
        assertNotNull(row[AuditLogTable.newValue])
    }

    @Test
    fun `recordUpdate writes correct audit row`() {
        transaction {
            AuditLogRepository.recordUpdate(
                tableName = tableName,
                recordId = recordId,
                oldFields = mapOf("name" to "Alice"),
                newFields = mapOf("name" to "Bob"),
                changedBy = callerId,
            )
        }

        val rows = findAuditRows()
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(AuditAction.UPDATE.name, row[AuditLogTable.action].name)
        assertEquals(callerId, row[AuditLogTable.changedBy])
        assertNotNull(row[AuditLogTable.oldValue])
        assertNotNull(row[AuditLogTable.newValue])
    }

    @Test
    fun `record writes branchId when provided`() {
        val branchId = UUID.randomUUID()
        DatabaseTestHelper.insertTestBranch(branchId)
        trackOwned(BranchTable, BranchTable.id, branchId)

        transaction {
            AuditLogRepository.record(
                tableName = tableName,
                recordId = recordId,
                action = AuditAction.INSERT,
                changedBy = callerId,
                branchId = branchId,
                newValue = """{"status":"active"}""",
            )
        }

        val rows = findAuditRows()
        assertEquals(1, rows.size)
        assertEquals(branchId, rows[0][AuditLogTable.branchId])
    }

    @Test
    fun `record leaves branchId null when omitted`() {
        transaction {
            AuditLogRepository.record(
                tableName = tableName,
                recordId = recordId,
                action = AuditAction.INSERT,
                changedBy = callerId,
            )
        }

        val rows = findAuditRows()
        assertEquals(1, rows.size)
        assertNull(rows[0][AuditLogTable.branchId])
    }

    @Test
    fun `recordDelete writes correct audit row`() {
        transaction {
            AuditLogRepository.recordDelete(
                tableName = tableName,
                recordId = recordId,
                oldFields = mapOf("name" to "Alice"),
                newFields = mapOf("name" to "Alice"),
                changedBy = callerId,
                reason = "test deletion",
            )
        }

        val rows = findAuditRows()
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(AuditAction.DELETE.name, row[AuditLogTable.action].name)
        assertEquals(callerId, row[AuditLogTable.changedBy])
        assertNotNull(row[AuditLogTable.oldValue])
        assertNotNull(row[AuditLogTable.newValue])
    }
}
