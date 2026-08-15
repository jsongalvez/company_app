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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [NotificationViewModel]'s unread-queue behavior per the #112 build of the locked #102
 * prototype (D1-D5): load-on-entry, pessimistic markRead (row moves to the in-memory Read section
 * only on 2xx — ADR-0022 axis), and markAllRead with the badge assigned from the endpoint's
 * authoritative response (#111 count semantics).
 *
 * Uses the #93 handler-based MockEngine (URL routing via `HttpRequestData`), `runTest(testScheduler)`
 * + `StandardTestDispatcher` main, and `runCurrent` drain — no poll loop here, so no `dispose()`
 * needed (the launches complete once drained).
 *
 * Join idiom (audit #141): 2xx mock responses drain inline under `runCurrent`, but NON-2xx
 * responses complete the launch's continuation on a real thread (Ktor response-pipeline dispatch),
 * so the state assignment lands after the drain. Where a test asserts the action-result state of a
 * failing call, capture the returned Job and `job.join()` after `runCurrent` — the assignment
 * happens before the launch completes, so the join makes the assertion deterministic.
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
                    mockApiClient(
                        // the bearer-auth plugin re-issues once on a 401, so the re-attempt must
                        // fail too (secondGetStatus) for the final response to be the 401
                        notificationsHandler(
                            listStatus = HttpStatusCode.Unauthorized,
                            secondGetStatus = HttpStatusCode.Unauthorized,
                        ),
                    ),
                )

            val job = vm.loadUnreadNotifications()
            runCurrent()
            // 401 → the bearer-auth re-attempt → final 401: the Error lands via the non-2xx
            // real-thread completion — join per the class KDoc idiom.
            job.join()

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
            job.join()

            val state = assertIs<UiState.Success<List<NotificationResponse>>>(vm.notifications.value)
            assertEquals(expected = listOf("n1", "n2"), actual = state.data.map { it.id })
            assertEquals(expected = emptyList<String>(), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 2, actual = NotificationState.unreadCount.value)
            // the failure must surface — the screen renders this inline (#135 class, audit #141)
            assertIs<UiState.Error>(vm.markReadResult.value)
        }

    @Test
    fun markRead_404_on_absent_row_reloads_list_and_skips_error_state() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm =
                NotificationViewModel(
                    mockApiClient(notificationsHandler(markStatus = HttpStatusCode.NotFound)),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            // Defense-in-depth: the backend 200s an already-read OWN row (idempotent), so a 404
            // can only mean absent/foreign — unreachable from this UI today; the VM handles it as
            // "the row is gone" (reload, no phantom error) if a future surface makes it reachable.
            val job = vm.markRead("n1")
            runCurrent()
            job.join()
            runCurrent() // drain the nested reload's GET (2xx — inline under runCurrent)

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
            // the mirror keeps the re-derived list in sync
            assertEquals(expected = listOf("n3"), actual = vm.freshestNotifications.value?.map { it.id })
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

    @Test
    fun markRead_during_reload_operates_on_last_list() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm =
                NotificationViewModel(
                    mockApiClient(
                        notificationsHandler(
                            secondGetDelayMs = HOLD_MS,
                            // The held reload serves the PRE-action snapshot (the read row still
                            // in it) — the genuinely stale body the stamp must neutralize. The
                            // re-issue (GET #3) serves the post-action truth [n2].
                            secondGetBody = NOTIFICATIONS_JSON,
                            thirdGetBody = N2_JSON,
                            dispatcher = StandardTestDispatcher(testScheduler),
                        ),
                    ),
                )

            vm.loadUnreadNotifications()
            runCurrent()
            assertEquals(expected = listOf("n1", "n2"), actual = vm.freshestNotifications.value?.map { it.id })

            // Reload in flight (GET held at +70s virtual): the rendered list stays via the
            // freshest flow, and a markRead during the reload must still move the row +
            // decrement the badge — the transform falls back to it when the state is Loading.
            vm.loadUnreadNotifications()
            runCurrent()
            assertIs<UiState.Loading>(vm.notifications.value)
            assertEquals(expected = listOf("n1", "n2"), actual = vm.freshestNotifications.value?.map { it.id })

            val job = vm.markRead("n1")
            runCurrent()
            job.join()

            assertEquals(expected = listOf("n2"), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 1, actual = NotificationState.unreadCount.value)

            // The stale pre-action snapshot lands: the stamp substitutes the post-action list —
            // n1 must NOT resurrect (a resurrect would re-render it unread under the decremented
            // badge and let a re-tap double-decrement).
            advanceTimeBy(HOLD_MS.milliseconds)
            runCurrent()
            assertEquals(expected = listOf("n2"), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 1, actual = NotificationState.unreadCount.value)

            // The re-issue lands the post-action truth and converges.
            advanceTimeBy(HOLD_MS.milliseconds)
            runCurrent()
            assertEquals(expected = listOf("n2"), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 1, actual = NotificationState.unreadCount.value)
        }

    @Test
    fun markAllRead_mid_reload_stale_landing_does_not_resurrect_rows() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm =
                NotificationViewModel(
                    mockApiClient(
                        notificationsHandler(
                            secondGetDelayMs = HOLD_MS,
                            secondGetBody = NOTIFICATIONS_JSON,
                            thirdGetBody = EMPTY_JSON,
                            dispatcher = StandardTestDispatcher(testScheduler),
                        ),
                    ),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            // Reload in flight (held), then Mark all: the transform empties the rendered list via
            // the freshest-flow fallback + sets the authoritative badge.
            vm.loadUnreadNotifications()
            runCurrent()
            val job = vm.markAllRead()
            runCurrent()
            job.join()

            assertEquals(expected = emptyList<String>(), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1", "n2"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)

            // The stale pre-markAll snapshot lands: the stamp substitutes the post-action list —
            // rows must NOT resurrect under the zero badge (they would re-render unread with the
            // Mark-all button back, unreachable by any in-screen refresh); Read stays
            // duplicate-free.
            advanceTimeBy(HOLD_MS.milliseconds)
            runCurrent()
            assertEquals(expected = emptyList<String>(), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1", "n2"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)

            // The re-issue lands the post-action truth (empty) and converges.
            advanceTimeBy(HOLD_MS.milliseconds)
            runCurrent()
            assertEquals(expected = emptyList<String>(), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1", "n2"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
        }

    @Test
    fun markAllRead_from_error_with_rendered_list_still_empties_it() =
        runTest(testScheduler) {
            NotificationState.setUnreadCount(2)
            val vm =
                NotificationViewModel(
                    mockApiClient(
                        notificationsHandler(secondGetStatus = HttpStatusCode.Forbidden),
                    ),
                )

            vm.loadUnreadNotifications()
            runCurrent()

            // The reload fails (403 — non-retried by the retry plugin, so the Error lands
            // deterministically) → Error; the freshest flow keeps the rendered list (keep-last),
            // so Mark all stays reachable and must empty the list + badge instead of no-oping
            // into a persistent rows-with-zero-badge divergence.
            vm.loadUnreadNotifications()
            runCurrent()
            assertIs<UiState.Error>(vm.notifications.value)
            assertEquals(expected = listOf("n1", "n2"), actual = vm.freshestNotifications.value?.map { it.id })

            val job = vm.markAllRead()
            runCurrent()
            job.join()

            assertEquals(expected = emptyList<String>(), actual = vm.freshestNotifications.value?.map { it.id })
            assertEquals(expected = listOf("n1", "n2"), actual = vm.readThisSession.value.map { it.id })
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
        }

    private fun notificationsHandler(
        listStatus: HttpStatusCode = HttpStatusCode.OK,
        markStatus: HttpStatusCode = HttpStatusCode.OK,
        markAllStatus: HttpStatusCode = HttpStatusCode.OK,
        markAllBody: String = MARK_ALL_JSON,
        secondGetDelayMs: Long = 0,
        secondGetStatus: HttpStatusCode = HttpStatusCode.OK,
        secondGetBody: String = ARRIVAL_JSON,
        thirdGetBody: String = ARRIVAL_JSON,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): MockRequestHandler {
        // First GET serves the two known rows; a reload GET (post-markAllRead with a nonzero
        // unreadCount, or an explicit second load) serves secondGetBody — the arrival-only list
        // by default, or a held STALE snapshot (the pre-action rows) for the stale-landing tests.
        // GET #3+ (the stamp's re-issued load) serves thirdGetBody — the post-action truth for
        // the stale-landing tests' converged state.
        var getCount = 0
        return { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath == "/api/notifications" -> {
                    getCount++
                    if (getCount == 1) {
                        jsonRespond(status = listStatus, body = NOTIFICATIONS_JSON)
                    } else {
                        // Virtualized hold (the badge-test pattern — withContext puts the delay on
                        // the test scheduler): keeps a reload in flight while the test drives
                        // actions against the rendered list.
                        if (secondGetDelayMs > 0) {
                            withContext(dispatcher) { delay(secondGetDelayMs) }
                        }
                        jsonRespond(
                            status = secondGetStatus,
                            body = if (getCount == 2) secondGetBody else thirdGetBody,
                        )
                    }
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

        const val EMPTY_JSON = """[]"""

        const val N2_JSON =
            """[{"id":"n2","sessionId":"s2","branchId":"b2","message":"Session at 10:00 AM — Maria Santos","isRead":false,"readAt":null,"createdAt":"2026-08-05T02:00:00+08:00"}]"""

        const val HOLD_MS = 70_000L

        const val MARK_ALL_JSON = """{"unreadCount":0}"""

        const val ARRIVAL_JSON =
            """[
                {"id":"n3","sessionId":"s3","branchId":"b3","message":"Session at 6:00 PM — New Arrival","isRead":false,"readAt":null,"createdAt":"2026-08-05T06:30:00+08:00"}
            ]"""
    }
}
