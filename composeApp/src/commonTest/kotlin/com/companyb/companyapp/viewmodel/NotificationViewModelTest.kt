package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for [NotificationViewModel]'s unread-queue behavior per the #112 build of the locked #102
 * prototype (D1-D5): load-on-entry, pessimistic markRead (row moves to the in-memory Read section
 * only on 2xx — ADR-0022 axis), and markAllRead with the badge assigned from the endpoint's
 * authoritative response (#111 count semantics).
 *
 * Uses the #93 handler-based MockEngine (URL routing via `HttpRequestData`), `runTest(testScheduler)`
 * + `StandardTestDispatcher` main, and `runCurrent` drain — no poll loop here, so no `dispose()`
 * needed (the launches complete once drained).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        NotificationState.clear()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        NotificationState.clear()
    }

    @Test
    fun loadUnreadNotifications_success_emits_success_list() =
        runTest(testScheduler) {
            val vm = NotificationViewModel(mockApiClient(notificationsHandler()))

            vm.loadUnreadNotifications()
            runCurrent()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n1", "n2"), actual = state.data.map { it.id })
        }

    @Test
    fun loadUnreadNotifications_failure_emits_error() =
        runTest(testScheduler) {
            val vm =
                NotificationViewModel(
                    mockApiClient(notificationsHandler(listStatus = HttpStatusCode.Unauthorized)),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            assertIs<UiState.Error>(vm.notifications.value)
        }

    @Test
    fun markRead_success_moves_row_to_read_section_and_decrements_badge() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm = NotificationViewModel(mockApiClient(notificationsHandler()))

            vm.loadUnreadNotifications()
            runCurrent()

            vm.markRead("n1")
            runCurrent()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n2"), actual = state.data.map { it.id })
            assertEquals(expected = listOf("n1"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 1, actual = NotificationState.unreadCount.value)
        }

    @Test
    fun markRead_failure_keeps_row_in_unread_section() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm =
                NotificationViewModel(
                    mockApiClient(notificationsHandler(markStatus = HttpStatusCode.InternalServerError)),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            val job = vm.markRead("n1")
            runCurrent()
            // 5xx mock responses complete the launch's continuation on a real thread (Ktor
            // response-pipeline dispatch — 2xx drains inline, non-2xx does not), so the Error
            // assignment lands after runCurrent; join() waits for the launch's completion, after
            // which the Error assignment is visible (#93 harness note).
            job.join()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n1", "n2"), actual = state.data.map { it.id })
            assertEquals(expected = emptyList<String>(), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 2, actual = NotificationState.unreadCount.value)
            // the failure must surface — the screen renders this inline (#135 class, audit #141)
            assertIs<UiState.Error>(vm.markReadResult.value)
        }

    @Test
    fun markRead_404_reloads_unread_list_and_skips_error_state() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm =
                NotificationViewModel(
                    mockApiClient(notificationsHandler(markStatus = HttpStatusCode.NotFound)),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            // The row was already read server-side (another device, or a markAll race where a
            // slow initial GET landed after markAll moved rows) — a 404 is the row being gone,
            // not an error: reload re-derives from the server, no phantom "failed" state.
            val job = vm.markRead("n1")
            runCurrent()
            // 404 completes on a real thread (same 5xx dispatch class) — join, then drain the
            // nested reload's GET (2xx — inline under runCurrent).
            job.join()
            runCurrent()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n3"), actual = state.data.map { it.id })
            assertEquals(expected = emptyList<String>(), actual = vm.readThisSession.value.map { it.id })
            assertIs<UiState.Idle>(vm.markReadResult.value)
        }

    @Test
    fun markAllRead_success_empties_unread_and_sets_badge_from_response() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(7)
            val vm = NotificationViewModel(mockApiClient(notificationsHandler()))

            vm.loadUnreadNotifications()
            runCurrent()

            vm.markAllRead()
            runCurrent()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = emptyList<String>(), actual = state.data.map { it.id })
            assertEquals(expected = listOf("n1", "n2"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
        }

    @Test
    fun markAllRead_failure_keeps_unread_list_and_badge() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(7)
            val vm =
                NotificationViewModel(
                    mockApiClient(notificationsHandler(markAllStatus = HttpStatusCode.InternalServerError)),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            val job = vm.markAllRead()
            runCurrent()
            // 5xx mock responses complete the launch's continuation on a real thread (Ktor
            // response-pipeline dispatch — 2xx drains inline, non-2xx does not), so the Error
            // assignment lands after runCurrent; join() waits for the launch's completion, after
            // which the Error assignment is visible (#93 harness note).
            job.join()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n1", "n2"), actual = state.data.map { it.id })
            assertEquals(expected = emptyList<String>(), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 7, actual = NotificationState.unreadCount.value)
            // the failure must surface — the screen renders this inline (#135 class, audit #141)
            assertIs<UiState.Error>(vm.markAllResult.value)
        }

    @Test
    fun markAllRead_with_concurrent_arrival_reloads_list_and_surfaces_new_row() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(1)
            val vm =
                NotificationViewModel(
                    mockApiClient(notificationsHandler(markAllBody = """{"unreadCount":1}""")),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            // markAll returns unreadCount=1 — an arrival committed before the count statement is
            // included in the authoritative count (#111 semantics); the VM must reload so the
            // screen surfaces the new row (no in-screen polling, D5).
            vm.markAllRead()
            runCurrent()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n3"), actual = state.data.map { it.id })
            assertEquals(expected = listOf("n1", "n2"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 1, actual = NotificationState.unreadCount.value)
        }

    @Test
    fun markRead_success_twice_does_not_duplicate_row_in_read_section() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm = NotificationViewModel(mockApiClient(notificationsHandler()))

            vm.loadUnreadNotifications()
            runCurrent()

            // Double-tap fires two PATCHes; the second success must not re-add the row.
            vm.markRead("n1")
            vm.markRead("n1")
            runCurrent()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n2"), actual = state.data.map { it.id })
            assertEquals(expected = listOf("n1"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 1, actual = NotificationState.unreadCount.value)
        }

    private fun notificationsHandler(
        listStatus: HttpStatusCode = HttpStatusCode.OK,
        markStatus: HttpStatusCode = HttpStatusCode.OK,
        markAllStatus: HttpStatusCode = HttpStatusCode.OK,
        markAllBody: String = MARK_ALL_JSON,
    ): MockRequestHandler {
        // First GET serves the two known rows; a reload GET (post-markAllRead with a nonzero
        // unreadCount) serves the arrival-only list — mirrors the #111 concurrent-arrival shape.
        var getCount = 0
        return { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/notifications" -> {
                    getCount++
                    jsonRespond(status = listStatus, body = if (getCount == 1) NOTIFICATIONS_JSON else ARRIVAL_JSON)
                }

                request.method == HttpMethod.Patch && request.url.encodedPath.startsWith("/api/notifications/") -> {
                    jsonRespond(status = markStatus, body = READ_JSON)
                }

                request.method == HttpMethod.Post && request.url.encodedPath == "/api/notifications/read-all" -> {
                    jsonRespond(status = markAllStatus, body = markAllBody)
                }

                else -> {
                    error("unexpected request: ${request.method} ${request.url.encodedPath}")
                }
            }
        }
    }

    private fun MockRequestHandleScope.jsonRespond(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        const val NOTIFICATIONS_JSON =
            """[
                {"id":"n1","sessionId":"s1","branchId":"b1","message":"Session at 2:00 PM — John Doe","isRead":false,"readAt":null,"createdAt":"2026-08-05T06:00:00+08:00"},
                {"id":"n2","sessionId":"s2","branchId":"b2","message":"Session at 10:00 AM — Maria Santos","isRead":false,"readAt":null,"createdAt":"2026-08-05T02:00:00+08:00"}
            ]"""

        const val READ_JSON =
            """{"id":"n1","sessionId":"s1","branchId":"b1","message":"Session at 2:00 PM — John Doe","isRead":true,"readAt":"2026-08-05T06:00:01+08:00","createdAt":"2026-08-05T06:00:00+08:00"}"""

        const val MARK_ALL_JSON = """{"unreadCount":0}"""

        const val ARRIVAL_JSON =
            """[
                {"id":"n3","sessionId":"s3","branchId":"b3","message":"Session at 6:00 PM — New Arrival","isRead":false,"readAt":null,"createdAt":"2026-08-05T06:30:00+08:00"}
            ]"""
    }
}
