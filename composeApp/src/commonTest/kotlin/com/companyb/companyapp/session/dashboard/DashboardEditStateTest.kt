package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #149 — the dashboard inline-edit state machine (#97 Q4 + ADR-0022) as pure tested
 * functions: the #142 discipline (the per-field machine converged only after the decision
 * points were extracted as pure tested functions).
 */
class DashboardEditStateTest {
    private fun row(
        id: String = "s1",
        type: String = "REGULAR",
        status: String = "COMPLETED",
        price: String = "2500.00",
        version: Int = 1,
    ) = DashboardSessionResponse(
        id = id,
        clientId = "c1",
        clientName = "Client",
        sessionType =
            com.companyb.companyapp.contracts.session.SessionType
                .valueOf(type),
        isWalkIn = false,
        sessionStatus =
            com.companyb.companyapp.contracts.session.SessionStatus
                .valueOf(status),
        basePrice = "2500.00",
        finalPrice = price,
        remarks = null,
        otherConcerns = null,
        bookedAt = null,
        nextAppointmentDate = null,
        version = version,
        isVoided = false,
    )

    @Test
    fun begin_edit_captures_draft_and_baseline_version() {
        val state = beginEdit(row(version = 3), DashboardEditField.STATUS)

        assertEquals("s1", state.sessionId)
        assertEquals(DashboardEditField.STATUS, state.field)
        assertEquals("COMPLETED", state.draft)
        assertEquals("COMPLETED", state.baselineValue)
        assertEquals(3, state.baselineVersion)
        assertFalse(state.inFlight)
        assertNull(state.error)
    }

    @Test
    fun draft_changed_is_false_for_same_value_and_true_for_new() {
        val state = beginEdit(row(status = "COMPLETED"), DashboardEditField.STATUS)
        val same = row(status = "COMPLETED")

        assertFalse(draftChanged(state, same))
        assertTrue(draftChanged(state, row(status = "PENDING")))
    }

    @Test
    fun draft_changed_uses_edit_start_value_when_row_is_newer() {
        val state =
            beginEdit(row(status = "PENDING", version = 1), DashboardEditField.STATUS)
                .withDraft("COMPLETED")

        assertTrue(draftChanged(state, row(status = "NO_SHOW", version = 2)))
    }

    @Test
    fun price_draft_compares_semantically() {
        val state = beginEdit(row(price = "2750.00"), DashboardEditField.FINAL_PRICE)

        // "2750" == "2750.00" == "2750.0" — an unchanged-draft commit must not dispatch.
        assertFalse(draftChanged(state, row(price = "2750")))
        assertFalse(draftChanged(state, row(price = "2750.0")))
        assertTrue(draftChanged(state, row(price = "3000.00")))
    }

    @Test
    fun final_price_input_validation_mirrors_the_backend_400() {
        assertTrue(finalPriceInputValid("2500"))
        assertTrue(finalPriceInputValid("2500.00"))
        assertTrue(finalPriceInputValid("2500.5"))
        assertTrue(finalPriceInputValid(" 2500 "))
        assertTrue(finalPriceInputValid("0"))
        assertFalse(finalPriceInputValid(""))
        assertFalse(finalPriceInputValid("  "))
        assertFalse(finalPriceInputValid("abc"))
        assertFalse(finalPriceInputValid("-5"))
        assertFalse(finalPriceInputValid("1,000"))
    }

    @Test
    fun as_in_flight_clears_prior_error_and_conflict() {
        val state =
            beginEdit(row(), DashboardEditField.STATUS)
                .asConflict("conflict")
                .asInFlight()

        assertTrue(state.inFlight)
        assertNull(state.error)
        assertFalse(state.conflict)
    }

    @Test
    fun conflict_keeps_draft_and_marks_the_conflict() {
        val state =
            beginEdit(row(), DashboardEditField.STATUS)
                .withDraft("SECOND_SESSION")
                .asInFlight()
                .asConflict("someone else")

        assertFalse(state.inFlight)
        assertTrue(state.conflict)
        assertEquals("SECOND_SESSION", state.draft)
        assertEquals("someone else", state.error)
    }

    @Test
    fun draft_edit_during_conflict_preserves_error_and_conflict() {
        val state =
            beginEdit(row(), DashboardEditField.STATUS)
                .withDraft("SECOND_SESSION")
                .asInFlight()
                .asConflict("someone else")
                .withDraft("SUBSEQUENT")

        // Pass-2 finding: with the conflict-state commit blocked, clearing the error on
        // typing would hide the Reload action and silently park the machine.
        assertTrue(state.conflict)
        assertEquals("someone else", state.error)
        assertEquals("SUBSEQUENT", state.draft)
    }

    @Test
    fun draft_edit_without_conflict_clears_error() {
        val state =
            beginEdit(row(), DashboardEditField.STATUS)
                .withDraft("SECOND_SESSION")
                .asInFlight()
                .asFailed("network down")
                .withDraft("SUBSEQUENT")

        assertNull(state.error)
    }

    @Test
    fun model_a_failure_keeps_draft_and_stays_in_edit() {
        val state =
            beginEdit(row(), DashboardEditField.STATUS)
                .withDraft("PENDING")
                .asInFlight()
                .asFailed("network down")

        assertFalse(state.inFlight)
        assertEquals("PENDING", state.draft)
        assertEquals("network down", state.error)
        assertFalse(state.conflict)
    }

    @Test
    fun after_reload_rebaselines_and_marks_remote_changes() {
        val state =
            beginEdit(row(version = 1, status = "COMPLETED"), DashboardEditField.STATUS)
                .withDraft("NO_SHOW")
                .asInFlight()
                .asConflict("someone else")

        val fresh = row(version = 2, status = "PENDING")
        val reloaded = state.afterReload(fresh)

        assertEquals(2, reloaded.baselineVersion)
        assertNull(reloaded.error)
        assertFalse(reloaded.conflict)
        assertTrue(reloaded.fieldChangedRemotely, "fresh PENDING != attempted NO_SHOW")
    }

    @Test
    fun after_reload_with_matching_draft_marks_nothing() {
        val state =
            beginEdit(row(version = 1, status = "COMPLETED"), DashboardEditField.STATUS)
                .withDraft("PENDING")
                .asInFlight()
                .asConflict("someone else")

        val reloaded = state.afterReload(row(version = 2, status = "PENDING"))

        assertFalse(reloaded.fieldChangedRemotely, "the user's draft matches the fresh value")
    }

    @Test
    fun after_reload_price_compares_semantically() {
        val state =
            beginEdit(row(version = 1, price = "2500.00"), DashboardEditField.FINAL_PRICE)
                .withDraft("2500")
                .asInFlight()
                .asConflict("someone else")

        assertFalse(state.afterReload(row(version = 2, price = "2500.00")).fieldChangedRemotely)
    }

    @Test
    fun merge_keeps_newer_version_and_drops_absent_rows() {
        val existing =
            listOf(
                row(id = "s1", version = 2, status = "COMPLETED"),
                row(id = "s2", version = 1),
            )
        val incoming =
            listOf(
                row(id = "s1", version = 1, status = "PENDING"),
                row(id = "s3", version = 1),
            )

        val merged = mergeDashboardRows(existing, incoming)

        assertEquals(2, merged.size)
        assertEquals("s1", merged[0].id)
        assertEquals(2, merged[0].version, "a stale poll must never regress a committed row")
        assertEquals("COMPLETED", merged[0].sessionStatus.name)
        assertEquals("s3", merged[1].id, "membership is backend-authoritative")
    }

    @Test
    fun merge_takes_incoming_when_equal_or_newer() {
        val existing = listOf(row(id = "s1", version = 2))
        val incoming = listOf(row(id = "s1", version = 2, status = "PENDING"))

        val merged = mergeDashboardRows(existing, incoming)

        assertEquals(1, merged.size)
        assertEquals("PENDING", merged[0].sessionStatus.name, "same version — incoming wins (backend-authoritative)")
    }

    @Test
    fun merge_with_null_existing_returns_incoming() {
        val incoming = listOf(row(id = "s1"))

        assertEquals(incoming, mergeDashboardRows(null, incoming))
    }

    @Test
    fun merge_with_pinned_id_keeps_the_edited_row_at_its_index() {
        val existing =
            listOf(
                row(id = "s1", version = 1),
                row(id = "s2", version = 1),
                row(id = "s3", version = 1),
            )
        // A landing that reorders s2 to the end (bookedAt moved elsewhere) must not
        // move the row under its open editor.
        val incoming =
            listOf(
                row(id = "s1", version = 1),
                row(id = "s3", version = 1),
                row(id = "s2", version = 1),
            )

        val merged = mergeDashboardRows(existing, incoming, pinnedId = "s2")

        assertEquals(listOf("s1", "s2", "s3"), merged.map { it.id })
    }

    @Test
    fun merge_without_pin_follows_incoming_order() {
        val existing =
            listOf(
                row(id = "s1", version = 1),
                row(id = "s2", version = 1),
            )
        val incoming =
            listOf(
                row(id = "s2", version = 1),
                row(id = "s1", version = 1),
            )

        assertEquals(listOf("s2", "s1"), mergeDashboardRows(existing, incoming).map { it.id })
    }

    @Test
    fun merge_with_unknown_pin_returns_incoming_order() {
        val existing = listOf(row(id = "s1", version = 1))
        val incoming = listOf(row(id = "s1", version = 1))

        assertEquals(listOf("s1"), mergeDashboardRows(existing, incoming, pinnedId = "gone").map { it.id })
    }
}
