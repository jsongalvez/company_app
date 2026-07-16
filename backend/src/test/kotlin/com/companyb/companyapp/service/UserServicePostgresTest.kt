package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()

    override fun initTestData() {
        DenyList.clear()
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(targetUserId, "target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
    }

    @Test
    fun `deactivate persists inactive status, writes audit log, and rejects existing token`() {
        DatabaseTestHelper.grantManageUsers(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val targetToken = JwtService.generateToken(targetUserId.toString())

        UserService.deactivate(callerId, targetUserId)

        assertEquals(UserStatus.INACTIVE, userStatus(targetUserId))
        assertTrue(DenyList.isDenied(targetUserId))
        assertNull(JwtService.verifyToken(targetToken))

        val auditEntry = latestAuditEntry(targetUserId)
        assertEquals("UPDATE", auditEntry.action)
        assertEquals(callerId.toString(), auditEntry.changedBy)
        assertEquals("ACTIVE", auditEntry.oldStatus)
        assertEquals("INACTIVE", auditEntry.newStatus)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `deactivate without MANAGE_USERS is allowed at service layer`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        UserService.deactivate(callerId, targetUserId)

        assertEquals(UserStatus.INACTIVE, userStatus(targetUserId))
        assertEquals(1L, auditEntryCount(targetUserId))
    }

    private fun userStatus(userId: UUID): UserStatus =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .single()[AppUserTable.status]
        }

    private fun latestAuditEntry(userId: UUID): AuditEntry =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where { (AuditLogTable.auditTableName eq "app_user") and (AuditLogTable.recordId eq userId) }
                    .orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()

            AuditEntry(
                action = row[AuditLogTable.action].name,
                changedBy = row[AuditLogTable.changedBy].toString(),
                oldStatus = DatabaseTestHelper.extractJsonField(row[AuditLogTable.oldValue] ?: "{}", "status"),
                newStatus = DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "status"),
            )
        }

    private fun auditEntryCount(userId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { (AuditLogTable.auditTableName eq "app_user") and (AuditLogTable.recordId eq userId) }
                .count()
        }

    private data class AuditEntry(
        val action: String,
        val changedBy: String,
        val oldStatus: String,
        val newStatus: String,
    )
}
