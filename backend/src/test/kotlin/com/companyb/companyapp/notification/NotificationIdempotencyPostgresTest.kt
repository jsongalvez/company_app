package com.companyb.companyapp.notification
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.notification.NotificationCreateParams
import com.companyb.companyapp.notification.NotificationRepository
import com.companyb.companyapp.notification.NotificationService
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.notification.decodeNotificationCursor
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.relief.ReliefNotifications
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #508 — durable notification idempotency and bounded mailbox reads: concurrent batches of
 * one occurrence deliver once per recipient with exact inserted counts, distinct repeat
 * occurrences survive, and keyset history pages stay stable across equal timestamps and
 * concurrent inserts.
 */
class NotificationIdempotencyPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val recipientA = TestFixtures.uuid()
    private val recipientB = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "idempotency-caller")
        IdentityFixtures.insertTestUser(recipientA, "idempotency-a")
        IdentityFixtures.insertTestUser(recipientB, "idempotency-b")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Idempotency Branch ${branchId.toString().take(8)}")
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
    fun `concurrent appointment batches deliver once per recipient with exact counts`() {
        val params =
            listOf(recipientA, recipientB).map { recipient ->
                NotificationCreateParams(
                    sessionId = sessionId,
                    userId = recipient,
                    branchId = branchId,
                    message = "You have an upcoming appointment on ${TestFixtures.today.plusDays(2)}",
                    eventType = NextAppointmentScheduler.APPOINTMENT_REMINDER,
                    sourceId = sessionId,
                    targetDate = TestFixtures.today.plusDays(2),
                )
            }

        val inserted = raceInsert(params)

        assertEquals(params.size, inserted.sum(), "exactly one delivery per recipient across racers")
        assertEquals(params.size, appointmentRowCount())
    }

    @Test
    fun `concurrent relief broadcasts deliver once per recipient`() {
        val sourceId = TestFixtures.uuid()
        val params =
            listOf(recipientA, recipientB).map { recipient ->
                NotificationCreateParams(
                    sessionId = null,
                    userId = recipient,
                    branchId = branchId,
                    message = "expiry broadcast",
                    eventType = ReliefNotifications.EXPIRED,
                    sourceId = sourceId,
                    targetDate = TestFixtures.today,
                )
            }

        val inserted = raceInsert(params)

        assertEquals(params.size, inserted.sum(), "exactly one delivery per recipient across racers")
        assertEquals(params.size, eventRowCount(ReliefNotifications.EXPIRED, sourceId))
    }

    @Test
    fun `later appointment occurrence survives while same-sweep retry dedups`() {
        val firstDate = TestFixtures.today.plusDays(2)
        val laterDate = TestFixtures.today.plusDays(9)

        fun appointmentOn(date: java.time.LocalDate) =
            NotificationCreateParams(
                sessionId = sessionId,
                userId = recipientA,
                branchId = branchId,
                message = "You have an upcoming appointment on $date",
                eventType = NextAppointmentScheduler.APPOINTMENT_REMINDER,
                sourceId = sessionId,
                targetDate = date,
            )

        assertEquals(1, NotificationRepository.insertBatch(listOf(appointmentOn(firstDate))))
        assertEquals(0, NotificationRepository.insertBatch(listOf(appointmentOn(firstDate))), "retry reuses the key")
        assertEquals(1, NotificationRepository.insertBatch(listOf(appointmentOn(laterDate))), "later date is new")

        assertEquals(2, appointmentRowCount())
    }

    @Test
    fun `distinct relief sources survive while same-source retry dedups`() {
        fun expiryFrom(sourceId: UUID) =
            NotificationCreateParams(
                sessionId = null,
                userId = recipientA,
                branchId = branchId,
                message = "expired unanswered",
                eventType = ReliefNotifications.EXPIRED,
                sourceId = sourceId,
                targetDate = TestFixtures.today,
            )

        val firstSource = TestFixtures.uuid()
        val secondSource = TestFixtures.uuid()
        assertEquals(1, NotificationRepository.insertBatch(listOf(expiryFrom(firstSource))))
        assertEquals(0, NotificationRepository.insertBatch(listOf(expiryFrom(firstSource))))
        assertEquals(1, NotificationRepository.insertBatch(listOf(expiryFrom(secondSource))))

        val bothSources =
            eventRowCount(ReliefNotifications.EXPIRED, firstSource) +
                eventRowCount(ReliefNotifications.EXPIRED, secondSource)
        assertEquals(2, bothSources)
    }

    @Test
    fun `revocation branch and direct notices both survive for one recipient`() {
        val inviteId = TestFixtures.uuid()
        val created =
            NotificationRepository.insertBatch(
                listOf(
                    NotificationCreateParams(
                        sessionId = null,
                        userId = recipientA,
                        branchId = branchId,
                        message = "X revoked Y's relief duty",
                        eventType = ReliefNotifications.INVITE_REVOKED,
                        sourceId = inviteId,
                        targetDate = TestFixtures.today,
                    ),
                    NotificationCreateParams(
                        sessionId = null,
                        userId = recipientA,
                        branchId = branchId,
                        message = "Your relief duty was revoked",
                        eventType = ReliefNotifications.INVITE_REVOKED,
                        sourceId = inviteId,
                        targetDate = TestFixtures.today,
                        dedupKey = "${ReliefNotifications.INVITE_REVOKED}:$inviteId:direct",
                    ),
                ),
            )

        assertEquals(2, created, "branch broadcast and explicit notice are distinct occurrences")
        assertEquals(2, eventRowCount(ReliefNotifications.INVITE_REVOKED, inviteId))
    }

    @Test
    fun `history pages cover equal timestamps without skips or duplicates`() {
        val ids = (1..HISTORY_ROWS).map { insertHistoryRow("row-$it", FIXED_CREATED_AT) }

        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val decoded = cursor?.let { decodeNotificationCursor(it) }
            val page = NotificationService.browseHistory(recipientA, decoded, PAGE_LIMIT)
            assertTrue(page.entries.size <= PAGE_LIMIT)
            seen.addAll(page.entries.map { it.id })
            cursor = page.nextCursor
            pages++
        } while (cursor != null)

        assertEquals(EXPECTED_PAGES, pages, "5 rows at limit 2 take 3 pages")
        assertEquals(ids.map { it.toString() }.toSet(), seen.toSet(), "every row exactly once")
        assertEquals(ids.map { it.toString() }.sortedDescending(), seen, "stable id-desc order on ties")
        assertNull(cursor)
    }

    @Test
    fun `rows inserted mid-paging do not shift fetched pages`() {
        val older = (1..PAGE_LIMIT * 2).map { insertHistoryRow("older-$it", FIXED_CREATED_AT) }

        val first = NotificationService.browseHistory(recipientA, null, PAGE_LIMIT)
        assertEquals(PAGE_LIMIT, first.entries.size)
        insertHistoryRow("newer-1", FIXED_CREATED_AT.plusHours(1))
        insertHistoryRow("newer-2", FIXED_CREATED_AT.plusHours(2))

        val second =
            NotificationService.browseHistory(
                recipientA,
                first.nextCursor?.let { decodeNotificationCursor(it) },
                PAGE_LIMIT,
            )

        assertEquals(PAGE_LIMIT, second.entries.size)
        val union = (first.entries + second.entries).map { it.id }.toSet()
        assertEquals(older.map { it.toString() }.toSet(), union, "newer inserts stay ahead of the cursor")
        assertNull(second.nextCursor)
    }

    @Test
    fun `countUnread counts only the caller's unread rows`() {
        val first = insertHistoryRow("unread-1", FIXED_CREATED_AT)
        insertHistoryRow("unread-2", FIXED_CREATED_AT)
        insertHistoryRow("other-user", FIXED_CREATED_AT, recipientB)
        NotificationService.markRead(recipientA, first)

        assertEquals(1, NotificationService.countUnread(recipientA))
        assertEquals(1, NotificationService.countUnread(recipientB))
        assertEquals(0, NotificationService.countUnread(callerId))
    }

    private fun appointmentRowCount(): Int =
        transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.sessionId eq sessionId }
                .count()
                .toInt()
        }

    private fun eventRowCount(
        eventType: String,
        sourceId: UUID,
    ): Int =
        transaction {
            NotificationTable
                .selectAll()
                .where {
                    (NotificationTable.eventType eq eventType) and
                        (NotificationTable.sourceId eq sourceId)
                }.count()
                .toInt()
        }

    private fun raceInsert(params: List<NotificationCreateParams>): List<Int> {
        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val futures =
            (1..THREADS).map {
                executor.submit<Int> {
                    ready.countDown()
                    start.await()
                    NotificationRepository.insertBatch(params)
                }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    private fun insertHistoryRow(
        message: String,
        createdAt: OffsetDateTime,
        recipientId: UUID = recipientA,
    ): UUID {
        // Locals, not members: inside insert{} the table receiver shadows same-named
        // members, so a member name would inline the COLUMN as the value.
        val rowId = TestFixtures.uuid()
        val rowMessage = message
        val rowCreatedAt = createdAt
        val rowUserId = recipientId
        val ownerBranchId = branchId
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = rowId
                it[NotificationTable.sessionId] = null
                it[NotificationTable.userId] = rowUserId
                it[NotificationTable.branchId] = ownerBranchId
                it[NotificationTable.message] = rowMessage
                it[NotificationTable.createdAt] = rowCreatedAt
                it[NotificationTable.dedupKey] = "TEST:$rowMessage:$rowId"
            }
        }
        return rowId
    }

    private companion object {
        private const val THREADS = 2
        private const val EXECUTOR_TIMEOUT_SECONDS = 30L
        private const val HISTORY_ROWS = 5
        private const val PAGE_LIMIT = 2
        private const val EXPECTED_PAGES = 3
        private val FIXED_CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2026-03-01T00:00:00Z")
    }
}
