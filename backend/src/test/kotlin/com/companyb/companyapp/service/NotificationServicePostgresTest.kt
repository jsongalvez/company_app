package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotificationServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        insertUser(callerId, "notification-caller")
        insertUser(otherUserId, "notification-other")
        insertBranch(branchId, "Test Branch ${branchId.toString().take(8)}")
        insertSession(sessionId, branchId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `listUnread returns only unread notifications for caller`() {
        val session2Id = UUID.randomUUID()
        insertSession(session2Id, branchId)
        insertNotification(sessionId, callerId, branchId)
        insertNotification(session2Id, callerId, branchId)

        val notifications = NotificationService.listUnread(callerId)

        assertEquals(2, notifications.size)
        assertTrue(notifications.all { !it.isRead })
        assertTrue(notifications.all { it.userId == callerId })
    }

    @Test
    fun `listUnread returns empty list when no unread notifications`() {
        val notifications = NotificationService.listUnread(callerId)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `listUnread excludes notifications for other users`() {
        insertNotification(sessionId, callerId, branchId)
        insertNotification(sessionId, otherUserId, branchId)

        val notifications = NotificationService.listUnread(callerId)

        assertEquals(1, notifications.size)
        assertEquals(callerId, notifications.first().userId)
    }

    @Test
    fun `markRead sets isRead and readAt on existing notification`() {
        val notification = insertNotification(sessionId, callerId, branchId)

        val result = NotificationService.markRead(callerId, notification.id)

        assertTrue(result.isRead)
        assertNotNull(result.readAt)
    }

    @Test
    fun `marked notification is excluded from subsequent listUnread`() {
        val notification = insertNotification(sessionId, callerId, branchId)

        NotificationService.markRead(callerId, notification.id)
        val remaining = NotificationService.listUnread(callerId)

        assertTrue(remaining.none { it.id == notification.id })
    }

    @Test
    fun `markRead throws 404 for non-existent notification`() {
        val fakeId = UUID.randomUUID()
        assertFailsWith<NotFoundResponse> {
            NotificationService.markRead(callerId, fakeId)
        }
    }

    @Test
    fun `markRead throws 404 when notification belongs to another user`() {
        val notification = insertNotification(sessionId, otherUserId, branchId)

        assertFailsWith<NotFoundResponse> {
            NotificationService.markRead(callerId, notification.id)
        }
    }

    private fun insertUser(
        userId: UUID,
        username: String,
    ) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "$username-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Test User $username",
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = BranchType.CLINIC
            }
        }
    }

    private fun insertSession(
        id: UUID,
        branchId: UUID,
    ) {
        val today = LocalDate.now(ZoneId.of("Asia/Manila"))
        val branchDayId =
            transaction {
                BranchDayTable.insertIgnore {
                    it[BranchDayTable.branchId] = branchId
                    it[BranchDayTable.date] = today
                }
                BranchDayTable
                    .selectAll()
                    .where {
                        (BranchDayTable.branchId eq branchId) and
                            (BranchDayTable.date eq today)
                    }.single()[BranchDayTable.id]
            }

        val clientId = UUID.randomUUID()
        transaction {
            ClientTable.insert {
                it[ClientTable.id] = clientId
                it[ClientTable.firstName] = "Test"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = "M"
                it[ClientTable.age] = 30
            }
            SessionTable.insert {
                it[SessionTable.id] = id
                it[SessionTable.clientId] = clientId
                it[SessionTable.branchDayId] = branchDayId
                it[SessionTable.sessionType] = SessionType.REGULAR
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
                it[SessionTable.basePrice] = BigDecimal("2500.00")
                it[SessionTable.finalPrice] = BigDecimal("2500.00")
                it[SessionTable.isWalkIn] = false
            }
        }
    }

    private fun insertNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.repository.model.Notification {
        val id = UUID.randomUUID()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
            }
        }
        return transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.id eq id }
                .single()
                .let { row ->
                    com.companyb.companyapp.repository.model.Notification(
                        id = row[NotificationTable.id],
                        sessionId = row[NotificationTable.sessionId],
                        userId = row[NotificationTable.userId],
                        branchId = row[NotificationTable.branchId],
                        isRead = row[NotificationTable.isRead],
                        readAt = row[NotificationTable.readAt],
                        createdAt = row[NotificationTable.createdAt],
                    )
                }
        }
    }

    private fun deleteTestRows() {
        val allTestUsers = listOf(callerId, otherUserId)
        transaction {
            NotificationTable.deleteAll()
            SessionVoidTable.deleteAll()
            SessionTable.deleteAll()
            UserRoleTable.deleteWhere { UserRoleTable.userId inList allTestUsers }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId inList allTestUsers }
            UserBranchAssignmentTable.deleteWhere {
                UserBranchAssignmentTable.userId inList allTestUsers
            }
            AuditLogTable.deleteWhere {
                AuditLogTable.changedBy inList allTestUsers
            }
            BranchDayTable.deleteWhere {
                BranchDayTable.branchId eq branchId
            }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            ClientTable.deleteAll()
            AppUserTable.deleteWhere { AppUserTable.id inList allTestUsers }
        }
    }
}
