package com.companyb.companyapp.service
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.NotificationCreateParams
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val otherUserId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
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
        val session2Id = TestFixtures.uuid()
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
        val fakeId = TestFixtures.uuid()
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

        // the failed call must NOT have mutated the other user's row (#141: the pre-fix version
        // updated by id first and only then threw 404 — the foreign row was silently consumed)
        val row =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.id eq notification.id }
                    .single()
            }
        assertFalse(row[NotificationTable.isRead])
        assertNull(row[NotificationTable.readAt])
    }

    @Test
    fun `markAllRead marks all unread as read and returns zero unread remaining`() {
        val n1 = insertNotificationForNewSession(callerId, branchId)
        NotificationService.markRead(callerId, n1.id)
        val n2 = insertNotificationForNewSession(callerId, branchId)

        val remaining = NotificationService.markAllRead(callerId)

        assertEquals(0, remaining)
        assertTrue(NotificationService.listUnread(callerId).isEmpty())
        val rows =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.userId eq callerId }
                    .map { it[NotificationTable.isRead] to it[NotificationTable.readAt] }
            }
        assertTrue(rows.all { (isRead, readAt) -> isRead && readAt != null })
    }

    @Test
    fun `markAllRead returns zero when no unread notifications exist`() {
        val remaining = NotificationService.markAllRead(callerId)
        assertEquals(0, remaining)
    }

    @Test
    fun `markAllRead is idempotent on re-call`() {
        val n1 = insertNotificationForNewSession(callerId, branchId)
        val n2 = insertNotificationForNewSession(callerId, branchId)

        val first = NotificationService.markAllRead(callerId)
        val second = NotificationService.markAllRead(callerId)

        assertEquals(0, first)
        assertEquals(0, second)
        assertTrue(NotificationService.listUnread(callerId).isEmpty())
    }

    @Test
    fun `markAllRead only marks caller's notifications`() {
        insertNotificationForNewSession(callerId, branchId)
        insertNotificationForNewSession(otherUserId, branchId)

        val remaining = NotificationService.markAllRead(callerId)

        assertEquals(0, remaining)
        assertEquals(1, NotificationService.listUnread(otherUserId).size)
        assertTrue(NotificationService.listUnread(callerId).isEmpty())
    }

    // #356 — storage widened (V25 dropped idx_notification_unique): a repeat event inserts a
    // new row rather than vanishing, and history lists read + unread indefinitely.
    @Test
    fun `repeat event inserts a new row rather than vanishing`() {
        val first = insertNotification(sessionId, callerId, branchId, "First ping")
        trackOwned(NotificationTable, NotificationTable.id, first.id)
        val second = insertNotification(sessionId, callerId, branchId, "Second ping")
        trackOwned(NotificationTable, NotificationTable.id, second.id)

        assertEquals(2, NotificationService.listHistory(callerId).size)
        assertTrue(NotificationService.listUnread(callerId).size == 2)
    }

    @Test
    fun `listHistory returns read and unread newest first for caller only`() {
        val older = insertNotificationForNewSession(callerId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, older.id)
        NotificationService.markRead(callerId, older.id)

        val newer = insertNotificationForNewSession(callerId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, newer.id)
        insertNotificationForNewSession(otherUserId, branchId).let {
            trackOwned(NotificationTable, NotificationTable.id, it.id)
        }

        val history = NotificationService.listHistory(callerId)

        assertEquals(listOf(newer.id, older.id), history.map { it.id })
        assertTrue(history.any { it.isRead } && history.any { !it.isRead })
    }

    @Test
    fun `null-session notification stores and reads without affecting session access`() {
        // Locals, not class properties: inside insert{} the table receiver shadows same-named
        // members, so `it[NotificationTable.branchId] = branchId` would inline the COLUMN as
        // the value (the documented Exposed insert{} receiver trap).
        val userId = callerId
        val ownerBranchId = branchId
        val reliefNotificationId = TestFixtures.uuid()
        trackOwned(NotificationTable, NotificationTable.id, reliefNotificationId)
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = reliefNotificationId
                it[NotificationTable.sessionId] = null
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = ownerBranchId
                it[NotificationTable.message] = "Relief event"
            }
        }

        assertTrue(NotificationService.listUnread(callerId).any { it.message == "Relief event" })
        assertTrue(NotificationRepository.existsForSessionAndUser(sessionId, callerId).not())
    }

    @Test
    fun `insertBatch deduplicates repeated session-user pairs across and within batches`() {
        val existing = insertNotification(sessionId, callerId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, existing.id)
        val duplicate =
            NotificationCreateParams(
                sessionId = sessionId,
                userId = callerId,
                branchId = branchId,
                message = "duplicate of the existing row",
            )

        val created = NotificationRepository.insertBatch(listOf(duplicate, duplicate))

        assertEquals(0, created)
        assertEquals(
            1,
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.sessionId eq sessionId }
                    .count()
                    .toInt()
            },
        )
    }

    private fun insertNotificationForNewSession(
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.repository.model.Notification {
        val newSessionId = TestFixtures.uuid()
        val newClientId = DatabaseTestHelper.insertTestClient()
        val newBranchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.insertTestSession(
            id = newSessionId,
            clientId = newClientId,
            branchDayId = newBranchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
        )
        trackOwned(SessionTable, SessionTable.id, newSessionId)
        trackOwned(ClientTable, ClientTable.id, newClientId)
        val notification = insertNotification(newSessionId, userId, branchId)
        trackOwned(NotificationTable, NotificationTable.id, notification.id)
        return notification
    }

    private fun insertNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
        message: String = "Test notification",
    ): com.companyb.companyapp.repository.model.Notification {
        val id = TestFixtures.uuid()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
                it[NotificationTable.message] = message
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
                        message = row[NotificationTable.message],
                        isRead = row[NotificationTable.isRead],
                        readAt = row[NotificationTable.readAt],
                        createdAt = row[NotificationTable.createdAt],
                    )
                }
        }
    }
}
