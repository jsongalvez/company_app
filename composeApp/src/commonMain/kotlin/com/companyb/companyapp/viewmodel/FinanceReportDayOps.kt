package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasBranchOrDayCapability
import com.companyb.companyapp.state.hasCapability
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.launch

// #479 — extracted from FinanceReportsViewModel so the file-function wall (TMF) stays
// honest. Extension functions on the ViewModel; FinanceReportsScreen method references and
// FinanceReportsViewModelTest (same package) resolve them unchanged.

internal fun FinanceReportsViewModel.selectDay(day: DailySalesSummaryResponse?) {
    selectedDayState.value = day
}

// ─────────────────────────── relief day entry (#158) ───────────────────────────

internal fun FinanceReportsViewModel.loadReliefDay(date: String) {
    val branchId =
        SessionState.snapshot.value.clock
            ?.branchId
            ?: run {
                reliefDayState.value = UiState.Error("No clocked-in branch")
                return
            }
    // Pass-1 HARD (P3/P4) — the #144-ack/browse stale-state class: a relief-day switch
    // while editing must not leave the OLD day's edit sections armed (editExpenses etc.
    // hold the previous day's rows; a later Edit toggle would mutate the wrong day).
    // Mirrors refreshWindowAndFeed's reset + loadSection's generation guard. The state
    // write is manual (the state-less #168 launch writes no Loading/Success) so a
    // superseded response never lands Success on the UI. The VM's [selectedBranchIdState] is
    // deliberately NOT touched:
    // the relief surface reads the clocked-in branch from SessionState (pass-2 HARD —
    // writing it would pin the hybrid's reports surface to the relief branch).
    reliefGeneration++
    val generation = reliefGeneration
    editModeState.value = false
    selectedDayState.value = null
    clearEditData()
    reliefDayState.value = UiState.Loading
    handler.launchStatelessGuarded(
        operation = "loadReliefDay",
        endpoint = "GET /api/branches/$branchId/daily-summary?date=$date",
        block = { apiClient.httpClient.get(ApiRoutes.branchDailySummaryWithDate(branchId, date)) },
        guarded =
            GuardedStateless(
                // #528 — decode/commit split: a relief-date switch mid-decode drops the body.
                decode = { it.body<DailySalesSummaryResponse>() },
                commit = { day ->
                    reliefDayState.value = UiState.Success(day)
                    selectDay(day)
                },
// #173 — the generation guard folds into the stale gate (a superseded relief
                // fetch — clearReliefState or a new date — must not write [reliefDay]/[selectedDay]).
                stale = { generation != reliefGeneration },
                onNonSuccess = { response ->
                    reliefDayState.value = UiState.Error("loadReliefDay failed: ${response.status.value}")
                },
                onError = { e ->
                    reliefDayState.value = UiState.Error(e.message ?: "Unknown error")
                },
            ),
    )
}

/**
 * #158 pass-2 — the hybrid exit path: leaving the relief section clears its state so a
 * re-entry via the chip starts clean (no stale day under a fresh date input).
 */
internal fun FinanceReportsViewModel.clearReliefState() {
    reliefGeneration++
    reliefDayState.value = UiState.Idle
    editModeState.value = false
    selectedDayState.value = null
    clearEditData()
}

/**
 * #105 D1 — Edit-toggle visibility, #156 branch-scoped: the checks resolve against the
 * VIEWED branch ([selectedBranchIdState] — the branch the day data belongs to, switchable via
 * the picker; defaults to the clocked-in branch). The backend resolves the same branch
 * from the day row (`CapabilityFilter.requireBranchCapability`), so the toggle, the
 * section loads and the backend 403s all agree. A null viewed branch fails closed.
 */
internal fun FinanceReportsViewModel.hasAssignCapability(): Boolean =
    SessionState.snapshot.value.capabilities.hasCapability(
        CapabilityCodes.ASSIGN_COMPENSATION,
        CapabilityContextType.BRANCH,
        selectedBranchIdState.value,
    )

/**
 * #105 D1/#156 — the EDIT_BRANCH_DATA leg of the edit-toggle check, now with the
 * #158 day leg: the BRANCH triple at the viewed branch OR a BRANCH_DAY relief grant
 * for the VIEWED day ([selectedDayState] — the day row the backend gates via; a relief
 * delegate edits their granted day without any BRANCH grant). A null day fails the
 * day leg closed.
 */
internal fun FinanceReportsViewModel.hasEditBranchDataCapability(): Boolean =
    SessionState.snapshot.value.capabilities.hasBranchOrDayCapability(
        CapabilityCodes.EDIT_BRANCH_DATA,
        selectedBranchIdState.value,
        selectedDayState.value?.branchDayId,
    )

internal fun FinanceReportsViewModel.hasEditCapabilities(): Boolean {
    val caps = SessionState.snapshot.value.capabilities
    val branchId = selectedBranchIdState.value
    // #158 — the EDIT_BRANCH_DATA leg includes the day-scoped relief grant; the
    // ASSIGN_COMPENSATION + EDIT_PAST_DAY legs stay BRANCH-only (not relief-eligible,
    // per the #157 surface).
    return hasEditBranchDataCapability() ||
        caps.hasCapability(CapabilityCodes.ASSIGN_COMPENSATION, CapabilityContextType.BRANCH, branchId) ||
        caps.hasCapability(CapabilityCodes.EDIT_PAST_DAY, CapabilityContextType.BRANCH, branchId)
}

internal fun FinanceReportsViewModel.setEditMode(on: Boolean) {
    if (on == editModeState.value) return
    editModeState.value = on
    if (on) {
        loadEditData()
    } else {
        clearEditData()
    }
}

// ─────────────────────────── edit data ───────────────────────────
