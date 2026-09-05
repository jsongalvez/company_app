package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.dto.UndoRemittanceRequest
import com.companyb.companyapp.dto.UpdateRemittanceHeaderRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow

// #479 — the remittance detail-mutation seam, extracted from RemittanceViewModel so the
// file-function wall (TMF) stays honest. Every entry point here mutates the loaded detail and
// shares ONE non-success axis (ADR-0022): 403 = capability revoked mid-session → silent exit
// (state Idle without Success, no error); 409 = version conflict → settle the mutation's state,
// raise the changed-elsewhere notice, and re-fetch the detail so the fresh payload wins.
//
// #484 — the axis lives in exactly one place: handleDetailMutationTerminal below. The 7
// adapters pass their state through it (no divergent copies).
//
// Screen call sites are unchanged: these are extension functions on the ViewModel, so
// `viewModel.addLine(...)` resolves identically in RemittanceDetailScreen and
// RemittanceViewModelTest. The receiver's handler/api/state plumbing is `internal` to this
// package on purpose — the seam exists so the axis lives in exactly one place.

/**
 * ADR-0022 409 axis — the ONE terminal 403/409 path shared by all 7 detail-mutation adapters.
 * 403 settles the mutation's state (Loading → Idle without Success = the screen's "silent
 * exit"); 409 additionally raises the notice, then re-fetches the detail so the screen renders
 * the fresh payload (the user's edit didn't win). Returns true when handled (the handler then
 * skips its generic Error); false for any other status.
 */
internal suspend fun <T> RemittanceViewModel.handleDetailMutationTerminal(
    state: MutableStateFlow<UiState<T>>,
    remittanceId: String,
    response: HttpResponse,
): Boolean =
    when (response.status) {
        HttpStatusCode.Forbidden -> {
            state.value = UiState.Idle
            true
        }

        HttpStatusCode.Conflict -> {
            state.value = UiState.Idle
            detailChangedNoticeState.value = true
            loadRemittance(remittanceId, resetNotice = false)
            true
        }

        else -> {
            false
        }
    }

internal fun RemittanceViewModel.addLine(
    remittanceId: String,
    request: CreateRemittanceLineRequest,
) {
    handler.launch(
        LaunchRequest(
            state = lineResultState,
            operation = "addLine",
            endpoint = "POST /api/remittances/$remittanceId/lines",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.remittanceLines(remittanceId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleDetailMutationTerminal(lineResultState, remittanceId, response)
            },
        ),
    )
}

internal fun RemittanceViewModel.deleteLine(
    remittanceId: String,
    lineId: String,
) {
    handler.launchUnit(
        state = deleteLineResultState,
        operation = "deleteLine",
        endpoint = "DELETE /api/remittances/$remittanceId/lines/$lineId",
        block = {
            apiClient.httpClient.delete(
                ApiRoutes.remittanceLine(remittanceId, lineId),
            )
        },
        hooks =
            LaunchHooks(
                onNonSuccess = { response ->
                    handleDetailMutationTerminal(deleteLineResultState, remittanceId, response)
                },
            ),
    )
}

internal fun RemittanceViewModel.addDayBreakdown(
    remittanceId: String,
    request: AddDayBreakdownRequest,
) {
    handler.launch(
        LaunchRequest(
            state = dayBreakdownResultState,
            operation = "addDayBreakdown",
            endpoint = "POST /api/remittances/$remittanceId/day-breakdowns",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.remittanceDayBreakdowns(remittanceId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleDetailMutationTerminal(dayBreakdownResultState, remittanceId, response)
            },
        ),
    )
}

// D4 — day-breakdown remove (#118 G5).
internal fun RemittanceViewModel.deleteDayBreakdown(
    remittanceId: String,
    breakdownId: String,
) {
    handler.launchUnit(
        state = dayBreakdownDeleteResultState,
        operation = "deleteDayBreakdown",
        endpoint = "DELETE /api/remittances/$remittanceId/day-breakdowns/$breakdownId",
        block = {
            apiClient.httpClient.delete(
                ApiRoutes.remittanceDayBreakdown(remittanceId, breakdownId),
            )
        },
        hooks =
            LaunchHooks(
                onNonSuccess = { response ->
                    handleDetailMutationTerminal(dayBreakdownDeleteResultState, remittanceId, response)
                },
            ),
    )
}

// D5 — submit (version-locked: expectedVersion comes from the detail).
internal fun RemittanceViewModel.submit(
    remittanceId: String,
    request: SubmitRemittanceRequest,
) {
    handler.launch(
        LaunchRequest(
            state = submitResultState,
            operation = "submit",
            endpoint = "POST /api/remittances/$remittanceId/submit",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.remittanceSubmit(remittanceId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleDetailMutationTerminal(submitResultState, remittanceId, response)
            },
        ),
    )
}

// D10 — undo within 48h (reason required; version-locked).
internal fun RemittanceViewModel.undo(
    remittanceId: String,
    request: UndoRemittanceRequest,
) {
    handler.launch(
        LaunchRequest(
            state = undoResultState,
            operation = "undo",
            endpoint = "POST /api/remittances/$remittanceId/undo",
            block = {
                apiClient.httpClient.post(
                    ApiRoutes.remittanceUndo(remittanceId),
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleDetailMutationTerminal(undoResultState, remittanceId, response)
            },
        ),
    )
}

// D9 — header PATCH (draft-only, version-locked).
internal fun RemittanceViewModel.updateHeader(
    remittanceId: String,
    request: UpdateRemittanceHeaderRequest,
) {
    handler.launch(
        LaunchRequest(
            state = headerUpdateResultState,
            operation = "updateHeader",
            endpoint = "PATCH /api/remittances/$remittanceId",
            entryMessage = "updateHeader called: remittanceId=$remittanceId",
            block = {
                apiClient.httpClient.patch(ApiRoutes.remittance(remittanceId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                handleDetailMutationTerminal(headerUpdateResultState, remittanceId, response)
            },
        ),
    )
}

// D6 — drift (frozen vs current), lazy: fetched on expander click only, cached after.
internal fun RemittanceViewModel.loadDrift(remittanceId: String) {
    handler.launch(
        LaunchRequest(
            state = driftState,
            operation = "loadDrift",
            endpoint = "GET /api/remittances/$remittanceId/drift",
            entryMessage = "loadDrift called: remittanceId=$remittanceId",
            block = { apiClient.httpClient.get(ApiRoutes.remittanceDrift(remittanceId)) },
            transform = { it.body() },
        ),
    )
}
