package com.companyb.companyapp.service

import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
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

class NotificationServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "notification-caller")
        DatabaseTestHelper.insertTestUser(otherUserId, "notification-other")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Branch ${branchId.toString().take(8)}")
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
        )
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        trackOwned(AppUserTable, AppUserTable.id, otherUserId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(ClientTable, ClientTable.id, clientId)
    }

    @Test
    fun `listUnread returns only unread notifications for caller`() {
        val session2Id = UUID.randomUUID()
        val client2Id = DatabaseTestHelper.insertTestClient()
        val branchDay2Id = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.insertTestSession(
            id = session2Id,
            clientId = client2Id,
            branchDayId = branchDay2Id,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
        )
        trackOwned(SessionTable, SessionTable.id, session2Id)
        trackOwned(ClientTable, ClientTable.id, client2Id)

        val n1 = insertNotification(sessionId, callerId, branchId)
        val n2 = insertNotification(session2Id, callerId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, n1.id)
        trackOwned(NotificationTable, NotificationTable.id, n2.id)

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
        val n1 = insertNotification(sessionId, callerId, branchId)
        val n2 = insertNotification(sessionId, otherUserId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, n1.id)
        trackOwned(NotificationTable, NotificationTable.id, n2.id)

        val notifications = NotificationService.listUnread(callerId)

        assertEquals(1, notifications.size)
        assertEquals(callerId, notifications.first().userId)
    }

    @Test
    fun `markRead sets isRead and readAt on existing notification`() {
        val notification = insertNotification(sessionId, callerId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, notification.id)

        val result = NotificationService.markRead(callerId, notification.id)

        assertTrue(result.isRead)
        assertNotNull(result.readAt)
    }

    @Test
    fun `marked notification is excluded from subsequent listUnread`() {
        val notification = insertNotification(sessionId, callerId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, notification.id)

        NotificationService.markRead(callerId, notification.id)
        val remaining = NotificationService.listUnread(callerId)

        assertTrue(remaining.none { it.id == notification.id })
    }

    @Test
    fun `markRead throws 404 for non-existent notification`() {
        val fakeId = UUID.randomUUID()
        assertFailsWith<NotFoundException> {
            NotificationService.markRead(callerId, fakeId)
        }
    }

    @Test
    fun `markRead throws 404 when notification belongs to another user`() {
        val notification = insertNotification(sessionId, otherUserId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, notification.id)

        assertFailsWith<NotFoundException> {
            NotificationService.markRead(callerId, notification.id)
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
}
