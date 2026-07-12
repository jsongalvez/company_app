package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.database.DatabaseConfig
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        ensureDatabase()
        DenyList.clear()
        deleteTestRows(callerId, targetUserId)
        insertUser(callerId, "caller")
        insertUser(targetUserId, "target")
    }

    @AfterTest
    fun tearDown() {
        DenyList.clear()
        if (isDatabaseReady()) {
            deleteTestRows(callerId, targetUserId)
        }
    }

    @Test
    fun `deactivate persists inactive status, writes audit log, and rejects existing token`() {
        grantManageUsers(callerId)
        val targetToken = JwtService.generateToken(targetUserId.toString())

        UserService.deactivate(callerId, targetUserId)

        assertEquals("INACTIVE", userStatus(targetUserId))
        assertTrue(DenyList.isDenied(targetUserId))
        assertNull(JwtService.verifyToken(targetToken))

        val auditEntry = latestAuditEntry(targetUserId)
        assertEquals("UPDATE", auditEntry.action)
        assertEquals(callerId.toString(), auditEntry.changedBy)
        assertEquals("ACTIVE", auditEntry.oldStatus)
        assertEquals("INACTIVE", auditEntry.newStatus)
    }

    @Test
    fun `deactivate without MANAGE_USERS is forbidden and leaves target active`() {
        assertFailsWith<ForbiddenResponse> {
            UserService.deactivate(callerId, targetUserId)
        }

        assertEquals("ACTIVE", userStatus(targetUserId))
        assertEquals(0, auditEntryCount(targetUserId))
    }

    private fun insertUser(
        id: UUID,
        usernameSuffix: String,
    ) {
        execSql(
            """
            INSERT INTO app_user (id, username, password_hash, status, email, display_name)
            VALUES (?::uuid, ?, ?, 'ACTIVE', ?, ?)
            """.trimIndent(),
            id.toString(),
            "pgsql-$usernameSuffix-$id",
            "test-password-hash",
            "pgsql-$usernameSuffix-$id@example.test",
            "Postgres $usernameSuffix",
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

    private fun userStatus(userId: UUID): String =
        querySingle(
            "SELECT status::text FROM app_user WHERE id = ?::uuid",
            userId.toString(),
        ) { it.getString(1) }

    private fun latestAuditEntry(userId: UUID): AuditEntry =
        querySingle(
            """
            SELECT action::text, changed_by::text, old_value->>'status', new_value->>'status'
            FROM audit_log
            WHERE table_name = 'app_user'
              AND record_id = ?::uuid
            ORDER BY changed_at DESC
            LIMIT 1
            """.trimIndent(),
            userId.toString(),
        ) {
            AuditEntry(
                action = it.getString(1),
                changedBy = it.getString(2),
                oldStatus = it.getString(3),
                newStatus = it.getString(4),
            )
        }

    private fun auditEntryCount(userId: UUID): Int =
        querySingle(
            """
            SELECT count(*)::int
            FROM audit_log
            WHERE table_name = 'app_user'
              AND record_id = ?::uuid
            """.trimIndent(),
            userId.toString(),
        ) { it.getInt(1) }

    private fun deleteTestRows(
        firstUserId: UUID,
        secondUserId: UUID,
    ) {
        val userIds = listOf(firstUserId.toString(), secondUserId.toString())
        execSql(
            "DELETE FROM audit_log WHERE changed_by IN (?::uuid, ?::uuid) OR record_id IN (?::uuid, ?::uuid)",
            userIds[0],
            userIds[1],
            userIds[0],
            userIds[1],
        )
        execSql("DELETE FROM user_capability WHERE user_id IN (?::uuid, ?::uuid)", userIds[0], userIds[1])
        execSql("DELETE FROM app_user WHERE id IN (?::uuid, ?::uuid)", userIds[0], userIds[1])
    }

    private data class AuditEntry(
        val action: String,
        val changedBy: String,
        val oldStatus: String,
        val newStatus: String,
    )

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
