package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
 * Tests for [NotificationBadgeViewModel]'s shell-scoped poll loop + dispose mechanism (#109 build).
 *
 * Demonstrates the #93 generalization of the MockEngine harness: handler-based mock with request
 * counting (exercises the multi-iteration poll-loop path the single-handler `mockApiClient(status,
 * body)` helper can't reach), `runTest` + `StandardTestDispatcher(testScheduler)` for virtual-time
 * control of `delay(REFRESH_INTERVAL_MS)`, and `viewModelScope.cancel()`-via-`dispose()` lifecycle
 * assertion that the poll loop halts on shell-leave.
 *
 * Drain uses `runCurrent` + explicit `advanceTimeBy`, NOT `advanceUntilIdle`: viewModelScope
 * coroutines are not tagged `BackgroundWork`, so the poll loop's `delay(REFRESH_INTERVAL_MS)` is a
 * foreground event — `advanceUntilIdle` would fast-forward through every iteration indefinitely.
 *
 * Mock-handler delays run on the engine's own context (real time — virtual-time advance only
 * reaches coroutines in viewModelScope), but the handler's delay CAN be virtualized by wrapping
 * it in a `withContext(StandardTestDispatcher(testScheduler))` inside the mock (see
 * `in_flight_poll_skips_overlapping_iterations`) — fully deterministic, no real sleeps.
 *
 * Each test ends with `vm.dispose()` to halt the poll loop *before* `runTest`'s internal drain: the
 * drain runs after [resetMain] (via [AfterTest]) in the per-class fixture pattern, so any viewModel
 * continuation there would find `Dispatchers.Main` unset and throw "platform dispatcher absent".
 * dispose() must also run even when an assertion fails mid-test — an uncancelled poll loop makes
 * runTest's final drain spin forever (virtual time never idles), so the poll-behavior tests dispose
 * in a `finally` block; the dispose-semantics test is the exception (dispose is its subject, so it
 * runs before the asserts by design).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBadgeViewModelTest {
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
    fun init_fires_first_poll_and_writes_notification_state() =
        runTest(testScheduler) {
            var notificationsCalls = 0
            var invitesCalls = 0
            val apiClient =
                mockApiClient(
                    notificationsHandler {
                        when (it.url.encodedPath) {
                            "/api/notifications/unread-count" -> notificationsCalls++
                            "/api/relief-invites" -> invitesCalls++
                        }
                    },
                )
            val vm = NotificationBadgeViewModel(apiClient)
            try {
                // Drain initial launches + first poll iteration: each poll loop's
                // `delay(REFRESH_INTERVAL_MS)` is queued at +60s; `runCurrent` runs everything
                // due at t=0 only.
                runCurrent()

                assertEquals(expected = 1, actual = notificationsCalls)
                assertEquals(expected = 1, actual = invitesCalls)
                assertIs<UiState.Success<Int>>(vm.pollResult.value)
                assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
                assertEquals(expected = 0, actual = NotificationState.inviteCount.value)
            } finally {
                // unconditional: an aborted assert must not leave the poll loop alive for the
                // final drain (class KDoc)
                vm.dispose()
            }
        }

    @Test
    fun advancing_sixty_seconds_fires_second_poll() =
        runTest(testScheduler) {
            var notificationsCalls = 0
            var invitesCalls = 0
            val apiClient =
                mockApiClient(
                    notificationsHandler {
                        when (it.url.encodedPath) {
                            "/api/notifications/unread-count" -> notificationsCalls++
                            "/api/relief-invites" -> invitesCalls++
                        }
                    },
                )
            val vm = NotificationBadgeViewModel(apiClient)
            try {
                runCurrent()
                assertEquals(expected = 1, actual = notificationsCalls)
                assertEquals(expected = 1, actual = invitesCalls)

                // advanceTimeBy brings virtual time to +60s (strict inequality means the resume-at-+60s
                // event is *not* auto-run by advanceTimeBy itself); runCurrent then drains the now-due
                // events (poll-loop resume + the inner launch the resume schedules). The poll loop's
                // next delay queues at +120s — outside advanceTimeBy's range.
                advanceTimeBy(60_000.milliseconds)
                runCurrent()

                assertEquals(expected = 2, actual = notificationsCalls)
                assertEquals(expected = 2, actual = invitesCalls)
                // The singleton StateFlow stays at the last successful poll's count (0 — count response)
                // after the second poll writes Success(Int) → NotificationState.setUnreadCount(data) again.
                assertIs<UiState.Success<Int>>(vm.pollResult.value)
                assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
                assertEquals(expected = 0, actual = NotificationState.inviteCount.value)
            } finally {
                vm.dispose()
            }
        }

    @Test
    fun dispose_cancels_poll_loop_no_further_polls_after_shell_leave() =
        runTest(testScheduler) {
            var notificationsCalls = 0
            var invitesCalls = 0
            val apiClient =
                mockApiClient(
                    notificationsHandler {
                        when (it.url.encodedPath) {
                            "/api/notifications/unread-count" -> notificationsCalls++
                            "/api/relief-invites" -> invitesCalls++
                        }
                    },
                )
            val vm = NotificationBadgeViewModel(apiClient)

            runCurrent()
            assertEquals(expected = 1, actual = notificationsCalls)
            assertEquals(expected = 1, actual = invitesCalls)

            // dispose() → viewModelScope.cancel(): the pending delay(REFRESH_INTERVAL_MS) resume is
            // removed from the scheduler (delay's continuation cancellation disposes its event).
            // Dispose is the subject here, so it deliberately runs before the asserts (the other
            // poll tests dispose in finally — their subject is the poll behavior).
            vm.dispose()

            advanceTimeBy(180_000.milliseconds)
            runCurrent()
            assertEquals(
                expected = 1,
                actual = notificationsCalls,
                message = "dispose() must cancel the poll loop — no further polls after shell-leave",
            )
            assertEquals(
                expected = 1,
                actual = invitesCalls,
                message = "dispose() must cancel the invite poll loop too",
            )
            // dispose() cancels viewModelScope but does NOT clear NotificationState (that's the App-level
            // AppSessionState.clear() pairing's job, per #109). The singleton StateFlow holds its last
            // successful value: 0 (one successful poll wrote it before dispose).
            assertIs<UiState.Success<Int>>(vm.pollResult.value)
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
            assertEquals(expected = 0, actual = NotificationState.inviteCount.value)
        }

    @Test
    fun in_flight_poll_skips_overlapping_iterations() =
        runTest(testScheduler) {
            var notificationsCalls = 0
            var invitesCalls = 0
            val apiClient =
                mockApiClient(
                    notificationsHandler(
                        onHit = { request ->
                            when (request.url.encodedPath) {
                                "/api/notifications/unread-count" -> notificationsCalls++
                                "/api/relief-invites" -> invitesCalls++
                            }
                        },
                        responseDelayMs = 70_000,
                        // Virtualize the handler's delay: the engine's own context is real time,
                        // but withContext on the test dispatcher puts the delay on the scheduler —
                        // fully deterministic, no real sleeps.
                        dispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val vm = NotificationBadgeViewModel(apiClient)
            try {
                // First poll's GETs are in flight (handler suspended at +70s virtual); the loops'
                // next iterations fire at +60s and must SKIP (in-flight guards) instead of
                // stacking a second request that could commit out of order.
                runCurrent()
                assertEquals(expected = 1, actual = notificationsCalls)
                assertEquals(expected = 1, actual = invitesCalls)
                assertIs<UiState.Loading>(vm.pollResult.value)
                assertIs<UiState.Loading>(vm.invitePollResult.value)

                advanceTimeBy(60_000.milliseconds)
                runCurrent()
                assertEquals(
                    expected = 1,
                    actual = notificationsCalls,
                    message = "in-flight guard must skip the +60s iteration while the GET is still pending",
                )
                assertEquals(
                    expected = 1,
                    actual = invitesCalls,
                    message = "the invite poll's own in-flight guard must skip too",
                )

                // Slow GETs land at +70s: Success writes the singletons, and the next polls fire
                // at +120s only once the guards clear.
                advanceTimeBy(10_000)
                runCurrent()
                assertIs<UiState.Success<Int>>(vm.pollResult.value)
                assertIs<UiState.Success<Int>>(vm.invitePollResult.value)
                assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
                assertEquals(expected = 0, actual = NotificationState.inviteCount.value)
                assertEquals(expected = 1, actual = notificationsCalls)
                assertEquals(expected = 1, actual = invitesCalls)

                advanceTimeBy(50_000)
                runCurrent()
                assertEquals(expected = 2, actual = notificationsCalls)
                assertEquals(expected = 2, actual = invitesCalls)
            } finally {
                // The final-drain (runTest internals) runs advanceUntilIdle OUTSIDE the timeout
                // — an uncancelled poll loop spins it forever, so dispose must be unconditional,
                // even when an assertion fails mid-test.
                vm.dispose()
            }
        }

    // #508 — the unread poll reads one integer, never the mailbox rows; invites still
    // serve the full received list for the actionable count.
    private fun notificationsHandler(
        responseDelayMs: Long = 0,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        onHit: (io.ktor.client.request.HttpRequestData) -> Unit,
    ): MockRequestHandler =
        {
            onHit(it)
            if (responseDelayMs > 0) {
                withContext(dispatcher) { delay(responseDelayMs) }
            }
            val body =
                if (it.url.encodedPath == "/api/notifications/unread-count") {
                    """{"unreadCount":0}"""
                } else {
                    "[]"
                }
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
}
