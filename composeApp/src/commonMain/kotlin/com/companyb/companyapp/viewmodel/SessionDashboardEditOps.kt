package com.companyb.companyapp.viewmodel

import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.ui.screen.DashboardEditField
import com.companyb.companyapp.ui.screen.DashboardEditState
import com.companyb.companyapp.ui.screen.afterReload
import com.companyb.companyapp.ui.screen.asConflict
import com.companyb.companyapp.ui.screen.asFailed
import com.companyb.companyapp.ui.screen.asInFlight
import com.companyb.companyapp.ui.screen.beginEdit
import com.companyb.companyapp.ui.screen.draftChanged
import com.companyb.companyapp.ui.screen.withDraft
import com.companyb.companyapp.ui.screen.withReason
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import kotlinx.coroutines.launch

// #479 — the #149 inline-edit machine, extracted from SessionDashboardViewModel so the
// file-function wall (TMF) stays honest: open/replace/discard/commit/dispatch/reload — with
// the ADR-0022 pessimistic transitions living inside the handler hooks (the #142/#147
// lesson) byte-preserved, including the 403 local-revocation and 409 conflict paths.
// Extension functions on the ViewModel; SessionDashboardScreen's method references
// (viewModel::startEdit etc.) and SessionDashboardViewModelTest resolve them unchanged.

private const val CONFLICT_MESSAGE =
    "This session was updated by someone else — reload to see the latest changes"

// --- #149 inline editing (desktop only, #97 Q4) ---

/**
 * Opens the editor on a cell. While a commit is in flight the click is ignored (the
 * blur-commit of a price editor dispatches first and owns the machine); otherwise an
 * existing editor is replaced — safe because a price editor with an edited draft
 * commits on blur (clicking another cell) before this runs, and a dropdown editor
 * only ever holds an unchanged draft until a selection commits.
 */
internal fun SessionDashboardViewModel.startEdit(
    sessionId: String,
    field: DashboardEditField,
) {
    val current = currentEditState.value
    if (!canEditState.value || !canReplaceEdit(current)) return
    val row = lastDataCache.value?.sessions?.firstOrNull { it.id == sessionId }
    if (row == null) {
        if (current != null && lastDataCache.value?.sessions?.none { it.id == current.sessionId } == true) {
            clearEdit()
        }
        return
    }
    if (!fieldEditAllowed(row, field)) return
    if (current?.error != null) clearEdit()
    editGeneration++
    currentEditState.value = beginEdit(row, field)
}

internal fun SessionDashboardViewModel.updateDraft(draft: String) {
    currentEditState.value = currentEditState.value?.withDraft(draft)
}

/** #403 — the reason input of the REMITTED-day editor. */
internal fun SessionDashboardViewModel.updateReason(reason: String) {
    currentEditState.value = currentEditState.value?.withReason(reason)
}

internal fun SessionDashboardViewModel.discardEdit() {
    val state = currentEditState.value ?: return
    if (state.inFlight) return
    clearEdit()
}

internal fun SessionDashboardViewModel.clearEdit() {
    if (currentEditState.value != null) {
        editGeneration++
        currentEditState.value = null
    }
}

/**
 * Pessimistic commit (ADR-0022): nothing changes on screen until the PATCH succeeds.
 * An unchanged draft exits without a request; an invalid price draft fails client-side
 * (the #135 parse-mirror pattern — the backend 400 never sees it); a conflict-state
 * commit is blocked (Reload is the sanctioned path). The post-dispatch machine
 * transitions all live inside the handler call (transform / onNonSuccess / onError) so
 * there is no second transition site to drift (the #142/#147 lesson); the pre-dispatch
 * guards above run before the request is sent.
 */
internal fun SessionDashboardViewModel.commitEdit() {
    val state = currentEditState.value ?: return
    // The 409 conflict is resolved by Reload, not by re-dispatching the same stale
    // version (a retry without reload is a guaranteed 409 — pass-1 finding: the
    // blur-commit on the Reload click re-dispatched the doomed PATCH and swallowed
    // the first Reload click). In-flight commits are likewise single-shot.
    if (state.inFlight || state.conflict) return
    if (prepareEdit(state) == null) return
    dispatchEdit(state)
}

internal fun SessionDashboardViewModel.prepareEdit(state: DashboardEditState): DashboardSessionResponse? {
    val row =
        if (canEditState.value) {
            lastDataCache.value?.sessions?.firstOrNull { it.id == state.sessionId }
        } else {
            null
        }
    val failure = row?.let { draftValidationFailure(state, it) }
    return when {
        row == null -> {
            clearEdit()
            null
        }

        !draftChanged(state, row) -> {
            logInfo("DashboardVM", "edit discarded — draft unchanged")
            clearEdit()
            null
        }

        failure == null -> {
            row
        }

        else -> {
            currentEditState.value = state.asFailed(failure)
            null
        }
    }
}

internal fun SessionDashboardViewModel.dispatchEdit(state: DashboardEditState) {
    val requestGeneration = editGeneration
    currentEditState.value = state.asInFlight()
    val request = editRequest(state)
    handler.launchStatelessGuarded(
        operation = "updateSession",
        endpoint = "PATCH ${request.path}",
        block = {
            apiClient.httpClient.patch(request.path) {
                setBody(request.body)
            }
        },
        guarded =
            GuardedStateless(
                // #528 — decode/commit split: a discard/new-edit mid-decode must not commit the
                // old row onto the new machine state.
                decode = { it.body<SessionResponse>() },
                commit = { updated ->
                    // The updated row is committed to the machine ([editState] carries what the UI
                    // renders — the #168 state-less launch has no result flow to write).
                    commitRow(updated)
                    clearEdit()
                },
                onNonSuccess = { response ->
                    when (response.status.value) {
                        // Q4: 403 — capability revoked mid-edit: silent exit + all status-edit
                        // affordances vanish. The route checks base edit authority before correction
                        // authority, so a status 403 must revoke both locally (fail closed).
                        403 -> {
                            logWarn("DashboardVM", "edit forbidden (403) — affordance hidden")
                            clearEdit()
                            locallyRevokedEditContext = capabilityContext
                            canEditState.value = false
                            if (state.field == DashboardEditField.STATUS) {
                                locallyRevokedCorrectionBranch = capabilityContext.first
                                canCorrectStatusState.value = false
                            }
                        }

                        // ADR-0022: 409 — version conflict: keep the draft + inline error +
                        // Reload action; reload re-baselines the version (never a silent
                        // lost update — the expectedVersion is the edit-start snapshot).
                        409 -> {
                            currentEditState.value = currentEditState.value?.asConflict(CONFLICT_MESSAGE)
                        }

                        // Model A: any other HTTP failure keeps the draft + inline error.
                        else -> {
                            currentEditState.value =
                                currentEditState.value?.asFailed(
                                    "Update failed (${response.status.value}) — retry or discard",
                                )
                        }
                    }
                },
                onError = {
                    currentEditState.value =
                        currentEditState.value?.asFailed("Update failed — check your connection and retry")
                },
                stale = { editGeneration != requestGeneration },
            ),
    )
}

/**
 * The 409 path's Reload action: refresh, then re-baseline the machine from the fresh
 * row. Guards (pass-1 findings): only re-baselines when the fresh row actually carries a
 * newer version (a failed or silently-cancelled refresh must NOT clear the conflict —
 * the user would retry a stale version forever with no visible failure); and only for
 * the machine still being edited (a discard or a new edit during the reload owns the
 * state).
 */
internal fun SessionDashboardViewModel.reloadAfterConflict() {
    val state = currentEditState.value ?: return
    if (state.inFlight || !state.conflict) return
    viewModelScope.launch {
        refresh().join()
        val current =
            currentEditState.value
                ?: return@launch
        if (current.sessionId != state.sessionId || current.field != state.field || !current.conflict) {
            return@launch
        }
        val row = lastDataCache.value?.sessions?.firstOrNull { it.id == current.sessionId } ?: return@launch
        if (row.version <= current.baselineVersion) {
            // No newer data landed (refresh was cancelled by an in-flight poll, or the
            // fetch failed) — the conflict stands; the user clicks Reload again.
            return@launch
        }
        currentEditState.value = current.afterReload(row)
    }
}
