package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * #94 Q3 — the inline login error copy maps ApiCallHandler's deterministic message format
 * ("$operation failed: ${status}") to the locked UX copy; everything else (network
 * exceptions) is the connection copy. Form stays enabled; credentials keep their values.
 */
class LoginErrorTextTest {
    @Test
    fun unauthorized_maps_to_invalid_credentials_copy() {
        assertEquals(
            "Invalid username or password.",
            loginErrorText(UiState.Error("login failed: 401")),
        )
    }

    @Test
    fun rate_limited_maps_to_too_many_attempts_copy() {
        assertEquals(
            "Too many attempts. Try again later.",
            loginErrorText(UiState.Error("login failed: 429")),
        )
    }

    @Test
    fun other_status_maps_to_connection_copy() {
        assertEquals(
            "Could not reach the server. Check your connection and try again.",
            loginErrorText(UiState.Error("login failed: 500")),
        )
    }

    @Test
    fun network_exception_message_maps_to_connection_copy() {
        assertEquals(
            "Could not reach the server. Check your connection and try again.",
            loginErrorText(UiState.Error("Connection refused")),
        )
    }

    @Test
    fun network_message_containing_401_does_not_map_to_credentials_copy() {
        assertEquals(
            "Could not reach the server. Check your connection and try again.",
            loginErrorText(UiState.Error("Failed to connect to host: port 40100")),
        )
    }

    @Test
    fun non_error_states_have_no_inline_copy() {
        assertNull(loginErrorText(UiState.Idle))
        assertNull(loginErrorText(UiState.Loading))
        assertNull(loginErrorText(UiState.Success(LoginResponse("token"))))
    }
}
