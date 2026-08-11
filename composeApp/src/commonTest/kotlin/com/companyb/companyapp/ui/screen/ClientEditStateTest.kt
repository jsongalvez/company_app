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
    fun `no dispatch record never abandons even when the draft matches`() {
        assertAbandon(
            expected = false,
            updateState = error,
            lastDispatched = null,
            editing = ClientField.AGE,
            draftMatches = true,
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

    @Test
    fun `same value on a different field never abandons`() {
        // The record's field must match the editing field — a coincidentally identical draft
        // value on another field is a different attempt.
        assertAbandon(
            expected = false,
            updateState = error,
            lastDispatched = ClientField.AGE,
            editing = ClientField.PHONE,
            draftMatches = true,
        )
    }

    @Test
    fun `matches trims the live draft - trailing space is not a modification`() {
        val record = DispatchedDraft(field = ClientField.PHONE, value = "09171234567")
        // The dispatch trimmed the payload; the user's draft still carries the trailing space —
        // byte-comparing raw would see a 'modification' and phantom-re-dispatch the same payload.
        assertEquals(
            expected = true,
            actual = record.matches(draftValue = "09171234567 ", bpSystolic = "", bpDiastolic = ""),
        )
        assertEquals(
            expected = false,
            actual = record.matches(draftValue = "09179999999", bpSystolic = "", bpDiastolic = ""),
        )
    }

    @Test
    fun `matches trims both bp sides - trailing spaces are not a modification`() {
        val record = DispatchedDraft(field = ClientField.BP_PAIR, value = "120", bpDiastolic = "80")
        assertEquals(
            expected = true,
            actual = record.matches(draftValue = "", bpSystolic = "120 ", bpDiastolic = " 80"),
        )
        assertEquals(
            expected = false,
            actual = record.matches(draftValue = "", bpSystolic = "120", bpDiastolic = "79"),
        )
    }

    @Test
    fun `matches parses bp numerics - leading zeros are not a modification`() {
        // "0121" and "121" parse to the same payload the server received — re-dispatching the
        // identical value must be treated as unchanged (the phantom class).
        val record = DispatchedDraft(field = ClientField.BP_PAIR, value = "121", bpDiastolic = "80")
        assertEquals(
            expected = true,
            actual = record.matches(draftValue = "", bpSystolic = "0121", bpDiastolic = "080"),
        )
    }

    @Test
    fun `matches parses age numerics - leading zeros are not a modification`() {
        val record = DispatchedDraft(field = ClientField.AGE, value = "30")
        assertEquals(
            expected = true,
            actual = record.matches(draftValue = "030", bpSystolic = "", bpDiastolic = ""),
        )
        assertEquals(
            expected = false,
            actual = record.matches(draftValue = "abc", bpSystolic = "", bpDiastolic = ""),
        )
    }

    @Test
    fun `bp record never matches a single-field draft`() {
        val record = DispatchedDraft(field = ClientField.BP_PAIR, value = "120", bpDiastolic = "80")
        assertEquals(
            expected = false,
            actual = record.matches(draftValue = "120", bpSystolic = "", bpDiastolic = ""),
        )
    }

    @Test
    fun `null record never matches`() {
        val record = DispatchedDraft(field = ClientField.PHONE, value = "")
        assertEquals(
            expected = false,
            actual = record.matches(draftValue = "123", bpSystolic = "", bpDiastolic = ""),
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
