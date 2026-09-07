package com.companyb.companyapp.notification
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.notification.NotificationCreateParams
import com.companyb.companyapp.notification.NotificationRepository
import com.companyb.companyapp.notification.NotificationService
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
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

    private companion object {
        private const val HISTORY_LIMIT = 20
    }

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "notification-caller")
        IdentityFixtures.insertTestUser(otherUserId, "notification-other")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Branch ${branchId.toString().take(8)}")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
        )
    }

    @Test
    fun `listUnread returns only unread notifications for caller`() {
        val session2Id = TestFixtures.uuid()
        val client2Id = SessionClientFixtures.insertTestClient()
        val branchDay2Id = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestSession(
            id = session2Id,
            clientId = client2Id,
            branchDayId = branchDay2Id,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
        )

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
        val fakeId = TestFixtures.uuid()
        assertFailsWith<NotFoundException> {
            NotificationService.markRead(callerId, fakeId)
        }
    }

    @Test
    fun `markRead throws 404 when notification belongs to another user`() {
        val notification = insertNotification(sessionId, otherUserId, branchId)

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
        insertNotificationForNewSession(callerId, branchId)

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
        insertNotificationForNewSession(callerId, branchId)
        insertNotificationForNewSession(callerId, branchId)

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

    // #356 — storage widened (pre-squash V25 dropped idx_notification_unique): a repeat event inserts a
    // new row rather than vanishing, and history lists read + unread indefinitely.
    @Test
    fun `repeat event inserts a new row rather than vanishing`() {
        insertNotification(sessionId, callerId, branchId, "First ping")
        insertNotification(sessionId, callerId, branchId, "Second ping")

        assertEquals(2, NotificationService.browseHistory(callerId, null, HISTORY_LIMIT).entries.size)
        assertTrue(NotificationService.listUnread(callerId).size == 2)
    }

    @Test
    fun `browseHistory returns read and unread newest first for caller only`() {
        val older = insertNotificationForNewSession(callerId, branchId)
        NotificationService.markRead(callerId, older.id)

        val newer = insertNotificationForNewSession(callerId, branchId)
        insertNotificationForNewSession(otherUserId, branchId).let {
        }

        val history = NotificationService.browseHistory(callerId, null, HISTORY_LIMIT)

        assertEquals(listOf(newer.id.toString(), older.id.toString()), history.entries.map { it.id })
        assertTrue(history.nextCursor == null)
        assertTrue(history.entries.any { it.isRead } && history.entries.any { !it.isRead })
    }

    @Test
    fun `null-session notification stores and reads without affecting session access`() {
        // Locals, not class properties: inside insert{} the table receiver shadows same-named
        // members, so `it[NotificationTable.branchId] = branchId` would inline the COLUMN as
        // the value (the documented Exposed insert{} receiver trap).
        val userId = callerId
        val ownerBranchId = branchId
        val reliefNotificationId = TestFixtures.uuid()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = reliefNotificationId
                it[NotificationTable.sessionId] = null
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = ownerBranchId
                it[NotificationTable.message] = "Relief event"
                it[NotificationTable.dedupKey] = "RELIEF_TEST:$reliefNotificationId"
            }
        }

        assertTrue(NotificationService.listUnread(callerId).any { it.message == "Relief event" })
        assertTrue(NotificationRepository.existsForSessionAndUser(sessionId, callerId).not())
    }

    // #358 — event rows (null session) are identified by (event, source, user) within a
    // batch: two different relief events for the same person in one command's broadcast
    // must both land; a repeated identical event must not.
    @Test
    fun `insertBatch keeps distinct event rows per person and drops in-batch event duplicates`() {
        val created =
            NotificationRepository.insertBatch(
                listOf(
                    NotificationCreateParams(
                        sessionId = null,
                        userId = callerId,
                        branchId = branchId,
                        message = "requested",
                        eventType = "RELIEF_REQUESTED",
                        sourceId = TestFixtures.uuid(),
                        targetDate = TestFixtures.today,
                    ),
                    NotificationCreateParams(
                        sessionId = null,
                        userId = callerId,
                        branchId = branchId,
                        message = "granted",
                        eventType = "RELIEF_REQUEST_GRANTED",
                        sourceId = TestFixtures.uuid(),
                        targetDate = TestFixtures.today,
                    ),
                ),
            )
        assertEquals(2, created)

        val repeatSource = TestFixtures.uuid()
        val repeatCreated =
            NotificationRepository.insertBatch(
                listOf(
                    NotificationCreateParams(
                        sessionId = null,
                        userId = callerId,
                        branchId = branchId,
                        message = "same event twice",
                        eventType = "RELIEF_INVITE_ACCEPTED",
                        sourceId = repeatSource,
                        targetDate = TestFixtures.today,
                    ),
                    NotificationCreateParams(
                        sessionId = null,
                        userId = callerId,
                        branchId = branchId,
                        message = "same event twice",
                        eventType = "RELIEF_INVITE_ACCEPTED",
                        sourceId = repeatSource,
                        targetDate = TestFixtures.today,
                    ),
                ),
            )
        assertEquals(1, repeatCreated)
    }

    @Test
    fun `insertBatch deduplicates repeated occurrence deliveries across and within batches`() {
        // #508 — occurrence identity (session + target date), not the raw pair, owns dedup:
        // seeding through the write path and repeating it inserts nothing.
        val targetDate = TestFixtures.today.plusDays(2)
        val duplicate =
            NotificationCreateParams(
                sessionId = sessionId,
                userId = callerId,
                branchId = branchId,
                message = "duplicate of the existing delivery",
                eventType = NextAppointmentScheduler.APPOINTMENT_REMINDER,
                sourceId = sessionId,
                targetDate = targetDate,
            )

        assertEquals(1, NotificationRepository.insertBatch(listOf(duplicate)))
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
    ): com.companyb.companyapp.notification.Notification {
        val newSessionId = TestFixtures.uuid()
        val newClientId = SessionClientFixtures.insertTestClient()
        val newBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestSession(
            id = newSessionId,
            clientId = newClientId,
            branchDayId = newBranchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
        )
        val notification = insertNotification(newSessionId, userId, branchId)
        return notification
    }

    private fun insertNotification(
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
        message: String = "Test notification",
    ): com.companyb.companyapp.notification.Notification {
        val id = TestFixtures.uuid()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
                it[NotificationTable.message] = message
                it[NotificationTable.dedupKey] = "APPT:$sessionId:$id"
            }
        }
        return transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.id eq id }
                .single()
                .let { row ->
                    com.companyb.companyapp.notification.Notification(
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
