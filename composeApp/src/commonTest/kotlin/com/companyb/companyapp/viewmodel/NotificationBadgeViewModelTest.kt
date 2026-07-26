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
 * Each test ends with `vm.dispose()` to halt the poll loop *before* `runTest`'s internal drain: the
 * drain runs after [resetMain] (via [AfterTest]) in the per-class fixture pattern, so any viewModel
 * continuation there would find `Dispatchers.Main` unset and throw "platform dispatcher absent".
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

    private fun notificationsHandler(onHit: () -> Unit): MockRequestHandler =
        {
            onHit()
            respond(
                content = ByteReadChannel("[]"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
}
