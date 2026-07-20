package com.companyb.companyapp

import com.companyb.companyapp.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComposeAppCommonTest {
    @Test
    fun `example math`() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun `UiState Idle is not Loading`() {
        val state: UiState<Int> = UiState.Idle
        assertIs<UiState.Idle>(state)
    }

    @Test
    fun `UiState Success wraps value`() {
        val state = UiState.Success(42)
        assertEquals(42, state.data)
    }
}
