package com.companyb.companyapp.async

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiStateTest {
    @Test
    fun `Idle is a singleton`() {
        assertTrue(UiState.Idle === UiState.Idle)
    }

    @Test
    fun `Loading is a singleton`() {
        assertTrue(UiState.Loading === UiState.Loading)
    }

    @Test
    fun `Success holds data`() {
        val state = UiState.Success("test data")
        assertIs<UiState.Success<String>>(state)
        assertEquals("test data", state.data)
    }

    @Test
    fun `Error holds message`() {
        val state = UiState.Error("something went wrong")
        assertIs<UiState.Error>(state)
        assertEquals("something went wrong", state.message)
    }

    @Test
    fun `sealed class is exhaustive`() {
        val states =
            listOf(
                UiState.Idle,
                UiState.Loading,
                UiState.Success(42),
                UiState.Error("fail"),
            )
        assertEquals(4, states.size)
        states.forEach { state ->
            when (state) {
                is UiState.Idle -> {}

                is UiState.Loading -> {}

                is UiState.Success -> {
                    assertTrue(state.data > 0)
                }

                is UiState.Error -> {
                    assertTrue(state.message.isNotEmpty())
                }
            }
        }
    }
}
