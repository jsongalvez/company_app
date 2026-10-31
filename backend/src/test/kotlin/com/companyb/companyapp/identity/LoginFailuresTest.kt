package com.companyb.companyapp.identity

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class LoginFailuresTest {
    private var now = 0L

    @AfterTest
    fun reset() = LoginFailures.resetForTest()

    private fun lockOut(name: String) {
        LoginFailures.clock = { now }
        repeat(LoginFailures.MAX_FAILURES) { LoginFailures.failed(name) }
    }

    @Test
    fun `a locked account opens again once the window ends`() {
        lockOut("victim")
        assertTrue(LoginFailures.isLocked("victim"))

        now += (LoginFailures.WINDOW - 1.minutes).inWholeNanoseconds
        assertTrue(LoginFailures.isLocked("victim"), "still inside the window")

        now += 2.minutes.inWholeNanoseconds
        assertFalse(LoginFailures.isLocked("victim"), "the real person can sign in after the window")
    }

    @Test
    fun `failures while locked do not stretch the lock`() {
        lockOut("victim")
        now += (LoginFailures.WINDOW - 1.minutes).inWholeNanoseconds
        repeat(50) { LoginFailures.failed("victim") }

        now += 2.minutes.inWholeNanoseconds
        assertFalse(LoginFailures.isLocked("victim"))
    }

    @Test
    fun `a right password clears the count`() {
        LoginFailures.clock = { now }
        repeat(LoginFailures.MAX_FAILURES - 1) { LoginFailures.failed("victim") }
        LoginFailures.succeeded("victim")
        LoginFailures.failed("victim")
        assertFalse(LoginFailures.isLocked("victim"))
    }
}
