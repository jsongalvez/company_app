package com.companyb.companyapp.api.routes

import com.companyb.companyapp.contracts.notification.NotificationHistoryResponse
import com.companyb.companyapp.contracts.notification.NotificationUnreadCountResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.notification.NotificationRoutes
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.reporting.DailySalesSummaryRoutes
import com.companyb.companyapp.reporting.ExportRoutes
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Route validation for exports, the daily summaries feed and notifications. */
class ReadRouteValidationTest : RouteValidationBase() {
    private fun notification(): UUID {
        val notificationId = TestFixtures.uuid()
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = notificationId
                it[NotificationTable.sessionId] = testSessionId
                it[NotificationTable.userId] = testUserId
                it[NotificationTable.branchId] = testBranchId
                it[NotificationTable.message] = "Test notification"
                it[NotificationTable.dedupKey] = "APPT:$testSessionId:$notificationId"
            }
        }
        return notificationId
    }

    // ──────────────────────────────────────────────
    // ExportRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `GET export report missing format returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-15&to=2024-01-16")

    @Test
    fun `GET export report invalid format returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-15&to=2024-01-16&format=csv")

    @Test
    fun `GET export report missing from returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?to=2024-01-15&format=xlsx")

    @Test
    fun `GET export report missing to returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-15&format=xlsx")

    @Test
    fun `GET export report invalid date returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=not-a-date&to=2024-01-15&format=xlsx")

    @Test
    fun `GET export report from after to returns 400`() =
        assertGet(400, "/api/branches/$testBranchId/export/report?from=2024-01-16&to=2024-01-15&format=xlsx")

    @Test
    fun `GET export report multi-branch unknown type returns 400`() =
        assertGet(400, "/api/branches/export/report?from=2024-01-15&to=2024-01-16&format=xlsx&type=SPA")

    @Test
    fun `GET export report multi-branch missing format returns 400`() =
        assertGet(400, "/api/branches/export/report?from=2024-01-15&to=2024-01-16")

    // ──────────────────────────────────────────────
    // DailySalesSummaryRoutes — paged daily-summaries feed
    // ──────────────────────────────────────────────

    @Test
    fun `GET daily-summaries returns 200 with seeded day`() {
        val response = testServer.client.get("/api/branches/$testBranchId/daily-summaries")
        assertEquals(200, response.code)
        val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
        assertEquals(1, body.entries.size)
        assertEquals(testBranchDayId.toString(), body.entries.single().branchDayId)
        assertEquals(TestFixtures.today.toString(), body.entries.single().date)
        assertNull(body.nextCursor)
    }

    @Test
    fun `GET daily-summaries returns 200 empty feed for branch without days`() {
        val noDaysBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(noDaysBranchId, "No-Days Branch")
        val response = testServer.client.get("/api/branches/$noDaysBranchId/daily-summaries")
        assertEquals(200, response.code, "a feed is 200 not 404 when the branch has no days")
        val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
        assertTrue(body.entries.isEmpty())
        assertNull(body.nextCursor)
    }

    @Test
    fun `GET daily-summaries rejects limit zero`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?limit=0")

    @Test
    fun `GET daily-summaries rejects limit above max`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?limit=101")

    @Test
    fun `GET daily-summaries rejects non-numeric limit`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?limit=abc")

    @Test
    fun `GET daily-summaries rejects malformed cursor`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?cursor=not-a-cursor")

    @Test
    fun `GET daily-summaries rejects malformed from date`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?from=2026-13-99")

    @Test
    fun `GET daily-summaries rejects malformed to date`() =
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?to=not-a-date")

    @Test
    fun `GET daily-summaries rejects from after to`() {
        val tomorrow = TestFixtures.today.plusDays(1)
        val yesterday = TestFixtures.today.minusDays(1)
        assertGet(400, "/api/branches/$testBranchId/daily-summaries?from=$tomorrow&to=$yesterday")
    }

    @Test
    fun `GET daily-summaries window filters the feed`() {
        val today = TestFixtures.today
        val yesterday = today.minusDays(1)
        val response =
            testServer.client.get(
                "/api/branches/$testBranchId/daily-summaries?from=$yesterday&to=$today",
            )
        assertEquals(200, response.code)
        val body = json.decodeFromString<DailySalesSummaryBrowseResponse>(response.body.string())
        assertEquals(1, body.entries.size, "the window admits only the seeded day")
        assertEquals(today.toString(), body.entries.single().date)
    }

    // ──────────────────────────────────────────────
    // NotificationRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST notifications read-all returns 200 and marks all unread as read`() {
        val notificationId = notification()
        val response = testServer.client.post("/api/notifications/read-all")
        assertEquals(200, response.code)
        assertTrue(response.body.string().contains("\"unreadCount\":0"))
        val isRead =
            transaction {
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.id eq notificationId }
                    .single()[NotificationTable.isRead]
            }
        assertTrue(isRead)
    }

    @Test
    fun `PATCH notification read still matches param route when read-all literal registered first`() {
        val notificationId = notification()
        assertPatch(200, "/api/notifications/$notificationId/read")
    }

    @Test
    fun `GET notifications unread-count returns count without rows`() {
        val response = testServer.client.get("/api/notifications/unread-count")
        assertEquals(200, response.code)
        val body = json.decodeFromString<NotificationUnreadCountResponse>(response.body.string())
        assertEquals(0, body.unreadCount)
    }

    @Test
    fun `GET notifications history pages bounded entries with cursor`() {
        val first = testServer.client.get("/api/notifications/history?limit=1")
        assertEquals(200, first.code)
        val firstBody = json.decodeFromString<NotificationHistoryResponse>(first.body.string())
        assertTrue(firstBody.entries.size <= 1)

        val second = testServer.client.get("/api/notifications/history?cursor=bad-cursor&limit=1")
        assertEquals(400, second.code)
    }
}
