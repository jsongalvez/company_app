package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.viewmodel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the per-field edit supersede gate ([shouldAbandonFailedDraft]) — the decision
 * that regressed twice during the #142 audit loop (pass-9's stale-Error abandonment, pass-10's
 * fieldError-scoped misfire). The gate is the screen's only pure decision point; the rest of
 * the edit state machine is composable code, covered by convention via the phased loop.
 */
class ClientEditStateTest {
    private val error = UiState.Error("updateClient failed: 500")

    @Test
    fun `unchanged failed draft abandons on switch`() {
        assertAbandon(
            expected = true,
            updateState = error,
            lastDispatched = ClientField.AGE,
            editing = ClientField.AGE,
            draftMatches = true,
        )
    }

    @Test
    fun `modified draft dispatches despite the last failure`() {
        assertAbandon(
            expected = false,
            updateState = error,
            lastDispatched = ClientField.AGE,
            editing = ClientField.AGE,
            draftMatches = false,
        )
    }

    @Test
    fun `stale error from a superseded field never abandons a different field`() {
        assertAbandon(
            expected = false,
            updateState = error,
            lastDispatched = ClientField.AGE,
            editing = ClientField.PHONE,
            draftMatches = false,
        )
    }

    @Test
    fun `no dispatch record never abandons`() {
        assertAbandon(
            expected = false,
            updateState = error,
            lastDispatched = null,
            editing = ClientField.AGE,
            draftMatches = false,
        )
    }

    @Test
    fun `validation error state never abandons - nothing dispatched`() {
        // updateState Idle: the last PATCH resolved (403/409) or never fired — the draft is a
        // never-dispatched edit, so it must take the commit path (which aborts on invalid).
        assertAbandon(
            expected = false,
            updateState = UiState.Idle,
            lastDispatched = ClientField.AGE,
            editing = ClientField.AGE,
            draftMatches = true,
        )
    }

    @Test
    fun `loading state never abandons - dispatch cannot have landed`() {
        assertAbandon(
            expected = false,
            updateState = UiState.Loading,
            lastDispatched = ClientField.AGE,
            editing = ClientField.AGE,
            draftMatches = true,
        )
    }

    @Test
    fun `success state never abandons - no failure to abandon`() {
        assertAbandon(
            expected = false,
            updateState = UiState.Success("ok"),
            lastDispatched = ClientField.AGE,
            editing = ClientField.AGE,
            draftMatches = true,
        )
    }

    private fun assertAbandon(
        expected: Boolean,
        updateState: UiState<*>,
        lastDispatched: ClientField?,
        editing: ClientField?,
        draftMatches: Boolean,
    ) {
        assertEquals(
            expected = expected,
            actual =
                shouldAbandonFailedDraft(
                    updateState = updateState,
                    lastDispatchedField = lastDispatched,
                    editingField = editing,
                    draftMatchesLastDispatched = draftMatches,
                ),
        )
    }
}
