package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.dto.ClockOutRequest
import com.companyb.companyapp.network.mockApiClient
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
 * #147 — the clock-out leg of the attendance surface: sync Loading pre-set (the #140 r1
 * pattern — the confirm button's in-flight guard must hold from the caller's frame) and the
 * pass-2 resetClockOut (a failed attempt's error must not persist into the next dialog open).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
    private lateinit var testScheduler: TestCoroutineScheduler

    @BeforeTest
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val clockOutJson =
        """{"id":"a1","branchDayId":"d1","userId":"u1","markedBy":"u1","clockIn":"2026-08-10T08:00:00+08:00","clockOut":"2026-08-10T17:00:00+08:00","isRelief":false}"""

    private fun clockOutHandler(status: HttpStatusCode): MockRequestHandler =
        {
            respond(
                content = ByteReadChannel(if (status.value < 400) clockOutJson else """{"error":"boom"}"""),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    @Test
    fun clockOut_presets_loading_synchronously_from_callers_frame() =
        runTest(testScheduler) {
            val vm = AttendanceViewModel(mockApiClient(clockOutHandler(HttpStatusCode.OK)))

            val job = vm.clockOut(ClockOutRequest(attendanceId = "a1"))
            // Before any dispatch: the guard must already hold (the #135 double-tap pattern).
            assertIs<UiState.Loading>(vm.clockOutState.value)

            runCurrent()
            assertIs<UiState.Success<com.companyb.companyapp.dto.ClockOutResponse>>(vm.clockOutState.value)
            job.join()
        }

    @Test
    fun resetClockOut_clears_stale_error_before_next_dialog_open() =
        runTest(testScheduler) {
            val vm = AttendanceViewModel(mockApiClient(clockOutHandler(HttpStatusCode.BadRequest)))

            vm.clockOut(ClockOutRequest(attendanceId = "a1"))
            runCurrent()
            assertIs<UiState.Error>(vm.clockOutState.value)

            vm.resetClockOut()
            assertEquals(UiState.Idle, vm.clockOutState.value)
        }
}
