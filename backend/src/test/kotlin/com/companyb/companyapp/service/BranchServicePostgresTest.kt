package com.companyb.companyapp.service

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.domain.BranchType
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val clinicId = UUID.randomUUID()
    private val provincialTourId = UUID.randomUUID()
    private val medicalMissionId = UUID.randomUUID()
    private val branchIds = listOf(clinicId, provincialTourId, medicalMissionId)

    @BeforeTest
    fun setUp() {
        ensureDatabase()
        deleteTestRows(callerId, branchIds)
        insertUser(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (isDatabaseReady()) {
            deleteTestRows(callerId, branchIds)
        }
    }

    @Test
    fun `create persists all branch types and writes audit row`() {
        grantManageUsers(callerId)

        val clinic = BranchService.create(callerId, clinicId, " Main Clinic ", BranchType.CLINIC)
        val tour = BranchService.create(callerId, provincialTourId, "Cebu Tour", BranchType.PROVINCIAL_TOUR)
        val mission = BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        assertTrue(clinic.created)
        assertTrue(tour.created)
        assertTrue(mission.created)
        assertEquals("Main Clinic", clinic.branch.name)
        assertEquals(BranchType.CLINIC, persistedBranchType(clinicId))
        assertEquals(BranchType.PROVINCIAL_TOUR, persistedBranchType(provincialTourId))
        assertEquals(BranchType.MEDICAL_MISSION, persistedBranchType(medicalMissionId))
        assertEquals(1, auditEntryCount(clinicId))
        assertEquals("Main Clinic", auditNewName(clinicId))
    }

    @Test
    fun `duplicate client generated id returns existing branch without extra audit`() {
        grantManageUsers(callerId)
        val first = BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)

        val duplicate = BranchService.create(callerId, clinicId, "Changed Name", BranchType.MEDICAL_MISSION)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Main Clinic", duplicate.branch.name)
        assertEquals(BranchType.CLINIC, duplicate.branch.branchType)
        assertEquals(1, auditEntryCount(clinicId))
    }

    @Test
    fun `find by id and list return persisted branches`() {
        grantManageUsers(callerId)
        BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        BranchService.create(callerId, medicalMissionId, "Free Mission", BranchType.MEDICAL_MISSION)

        val found = BranchService.findById(medicalMissionId)
        val branchIds = BranchService.findAll().map { it.id }.toSet()

        assertEquals("Free Mission", found.name)
        assertTrue(clinicId in branchIds)
        assertTrue(medicalMissionId in branchIds)
    }

    @Test
    fun `create without MANAGE_USERS is forbidden and does not insert branch`() {
        assertFailsWith<ForbiddenResponse> {
            BranchService.create(callerId, clinicId, "Main Clinic", BranchType.CLINIC)
        }

        assertFalse(branchExists(clinicId))
        assertEquals(0, auditEntryCount(clinicId))
    }

    private fun insertUser(userId: UUID) {
        execSql(
            """
            INSERT INTO app_user (id, username, password_hash, status, email, display_name)
            VALUES (?::uuid, ?, ?, 'ACTIVE', ?, ?)
            """.trimIndent(),
            userId.toString(),
            "branch-caller-$userId",
            "test-password-hash",
            "branch-caller-$userId@example.test",
            "Branch Caller",
        )
    }

    private fun grantManageUsers(userId: UUID) {
        execSql(
            """
            INSERT INTO user_capability
                (user_id, capability_id, context_type, context_id, source_type, source_id, priority)
            SELECT ?::uuid, c.id, 'GLOBAL'::capability_context_type, ?::uuid,
                   'SYSTEM'::capability_source_type, ?::uuid, 100
            FROM capability c
            WHERE c.code = 'MANAGE_USERS'
            """.trimIndent(),
            userId.toString(),
            CapabilityService.GLOBAL_CONTEXT_ID.toString(),
            sourceId.toString(),
        )
    }

    private fun persistedBranchType(branchId: UUID): BranchType =
        querySingle(
            "SELECT branch_type::text FROM branch WHERE id = ?::uuid",
            branchId.toString(),
        ) { BranchType.valueOf(it.getString(1)) }

    private fun branchExists(branchId: UUID): Boolean =
        querySingle(
            "SELECT EXISTS (SELECT 1 FROM branch WHERE id = ?::uuid)",
            branchId.toString(),
        ) { it.getBoolean(1) }

    private fun auditEntryCount(branchId: UUID): Int =
        querySingle(
            """
            SELECT count(*)::int
            FROM audit_log
            WHERE table_name = 'branch'
              AND record_id = ?::uuid
            """.trimIndent(),
            branchId.toString(),
        ) { it.getInt(1) }

    private fun auditNewName(branchId: UUID): String =
        querySingle(
            """
            SELECT new_value->>'name'
            FROM audit_log
            WHERE table_name = 'branch'
              AND record_id = ?::uuid
            ORDER BY changed_at DESC
            LIMIT 1
            """.trimIndent(),
            branchId.toString(),
        ) { it.getString(1) }

    private fun deleteTestRows(
        userId: UUID,
        branchIds: List<UUID>,
    ) {
        val firstBranchId = branchIds[0].toString()
        val secondBranchId = branchIds[1].toString()
        val thirdBranchId = branchIds[2].toString()
        execSql(
            """
            DELETE FROM audit_log
            WHERE changed_by = ?::uuid
               OR record_id IN (?::uuid, ?::uuid, ?::uuid)
            """.trimIndent(),
            userId.toString(),
            firstBranchId,
            secondBranchId,
            thirdBranchId,
        )
        execSql("DELETE FROM user_capability WHERE user_id = ?::uuid", userId.toString())
        execSql(
            "DELETE FROM branch WHERE id IN (?::uuid, ?::uuid, ?::uuid)",
            firstBranchId,
            secondBranchId,
            thirdBranchId,
        )
        execSql("DELETE FROM app_user WHERE id = ?::uuid", userId.toString())
    }

    private companion object {
        private var databaseReady = false

        fun ensureDatabase() {
            if (!databaseReady) {
                DatabaseConfig.runMigrations()
                DatabaseConfig.runExposed()
                databaseReady = true
            }
        }

        fun isDatabaseReady(): Boolean = databaseReady

        fun execSql(
            sql: String,
            vararg args: String,
        ) {
            transaction {
                exec(sql, args = args.map { TextColumnType() to it })
            }
        }

        fun <T> querySingle(
            sql: String,
            vararg args: String,
            transform: (java.sql.ResultSet) -> T,
        ): T =
            transaction {
                exec(sql, args = args.map { TextColumnType() to it }) { rs ->
                    check(rs.next()) { "Expected one row for query: $sql" }
                    transform(rs)
                }
            } ?: error("Query did not return a result: $sql")
    }
}
