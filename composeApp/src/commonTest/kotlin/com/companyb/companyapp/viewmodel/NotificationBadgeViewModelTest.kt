package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.network.mockApiClient
import com.companyb.companyapp.state.NotificationState
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
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
 * Mock-handler delays are REAL time (the engine runs the handler on its own context, outside the
 * test scheduler) — virtual-time advance only reaches coroutines in viewModelScope. Tests that
 * need a slow response must pair a small real handler delay with a generous real `Thread.sleep`
 * wait, then `runCurrent` to drain the re-dispatched continuation (see
 * `in_flight_poll_skips_overlapping_iterations`).
 *
 * Each test ends with `vm.dispose()` to halt the poll loop *before* `runTest`'s internal drain: the
 * drain runs after [resetMain] (via [AfterTest]) in the per-class fixture pattern, so any viewModel
 * continuation there would find `Dispatchers.Main` unset and throw "platform dispatcher absent".
 * dispose() must also run even when an assertion fails mid-test — an uncancelled poll loop makes
 * runTest's final drain spin forever (virtual time never idles).
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
            var requestCount = 0
            val apiClient = mockApiClient(notificationsHandler { requestCount++ })
            val vm = NotificationBadgeViewModel(apiClient)

            // Drain initial launches + first poll iteration: poll loop's `delay(REFRESH_INTERVAL_MS)`
            // is queued at +60s; `runCurrent` runs everything due at t=0 only.
            runCurrent()

            assertEquals(expected = 1, actual = requestCount)
            assertIs<UiState.Success<Int>>(vm.pollResult.value)
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)

            vm.dispose()
        }

    @Test
    fun advancing_sixty_seconds_fires_second_poll() =
        runTest(testScheduler) {
            var requestCount = 0
            val apiClient = mockApiClient(notificationsHandler { requestCount++ })
            val vm = NotificationBadgeViewModel(apiClient)

            runCurrent()
            assertEquals(expected = 1, actual = requestCount)

            // advanceTimeBy brings virtual time to +60s (strict inequality means the resume-at-+60s
            // event is *not* auto-run by advanceTimeBy itself); runCurrent then drains the now-due
            // events (poll-loop resume + the inner launch the resume schedules). The poll loop's
            // next delay queues at +120s — outside advanceTimeBy's range.
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(expected = 2, actual = requestCount)
            // The singleton StateFlow stays at the last successful poll's count (0 — empty list response)
            // after the second poll writes Success(Int) → NotificationState.setUnreadCount(data) again.
            assertIs<UiState.Success<Int>>(vm.pollResult.value)
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)

            vm.dispose()
        }

    @Test
    fun dispose_cancels_poll_loop_no_further_polls_after_shell_leave() =
        runTest(testScheduler) {
            var requestCount = 0
            val apiClient = mockApiClient(notificationsHandler { requestCount++ })
            val vm = NotificationBadgeViewModel(apiClient)

            runCurrent()
            assertEquals(expected = 1, actual = requestCount)

            // dispose() → viewModelScope.cancel(): the pending delay(REFRESH_INTERVAL_MS) resume is
            // removed from the scheduler (delay's continuation cancellation disposes its event).
            vm.dispose()

            advanceTimeBy(180_000)
            runCurrent()
            assertEquals(
                expected = 1,
                actual = requestCount,
                message = "dispose() must cancel the poll loop — no further polls after shell-leave",
            )
            // dispose() cancels viewModelScope but does NOT clear NotificationState (that's the App-level
            // SessionState.clear() pairing's job, per #109). The singleton StateFlow holds its last
            // successful value: 0 (one successful poll wrote it before dispose).
            assertIs<UiState.Success<Int>>(vm.pollResult.value)
            assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
        }

    @Test
    fun in_flight_poll_skips_overlapping_iterations() =
        runTest(testScheduler) {
            var requestCount = 0
            val apiClient =
                mockApiClient(
                    notificationsHandler(
                        onHit = { requestCount++ },
                        responseDelayMs = 500L,
                    ),
                )
            val vm = NotificationBadgeViewModel(apiClient)
            try {
                // First poll's GET is in flight (the mock handler's delay is REAL time — the
                // engine runs the handler on its own context; only the poll LOOP's delay is
                // virtual, in viewModelScope). The loop's next iteration fires at +60s virtual
                // and must SKIP (in-flight guard) instead of stacking a second request that
                // could commit out of order.
                runCurrent()
                assertEquals(expected = 1, actual = requestCount)
                assertIs<UiState.Loading>(vm.pollResult.value)

                advanceTimeBy(60_000.milliseconds)
                runCurrent()
                assertEquals(
                    expected = 1,
                    actual = requestCount,
                    message = "in-flight guard must skip the +60s iteration while the GET is still pending",
                )

                // Real wait for the slow GET to land; its continuation re-dispatches to the
                // test scheduler, so runCurrent drains the collector write.
                Thread.sleep(700)
                runCurrent()
                assertIs<UiState.Success<Int>>(vm.pollResult.value)
                assertEquals(expected = 0, actual = NotificationState.unreadCount.value)
                assertEquals(expected = 1, actual = requestCount)

                // +120s virtual: the loop's next iteration fires only once the guard cleared
                // (+60s + 60s — the first advance brought virtual time to +60s).
                advanceTimeBy(60_000.milliseconds)
                runCurrent()
                assertEquals(expected = 2, actual = requestCount)
            } finally {
                // The final-drain (TestBuilders.kt:402) runs advanceUntilIdle OUTSIDE the timeout
                // — an uncancelled poll loop spins it forever, so dispose must be unconditional,
                // even when an assertion fails mid-test.
                vm.dispose()
            }
        }

    private fun notificationsHandler(
        responseDelayMs: Long = 0,
        onHit: () -> Unit,
    ): MockRequestHandler =
        {
            onHit()
            if (responseDelayMs > 0) {
                delay(responseDelayMs.milliseconds)
            }
            respond(
                content = ByteReadChannel("[]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
}
