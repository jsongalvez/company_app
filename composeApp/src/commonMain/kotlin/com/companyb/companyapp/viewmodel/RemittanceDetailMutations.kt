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
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow

// #479 — the remittance detail-mutation seam, extracted from RemittanceViewModel so the
// file-function wall (TMF) stays honest. Every entry point here mutates the loaded detail and
// shares ONE non-success axis (ADR-0022): 403 = capability revoked mid-session → silent exit
// (state Idle without Success, no error); 409 = version conflict → settle the mutation's state,
// raise the changed-elsewhere notice, and re-fetch the detail so the fresh payload wins.
//
// Screen call sites are unchanged: these are extension functions on the ViewModel, so
// `viewModel.addLine(...)` resolves identically in RemittanceDetailScreen and
// RemittanceViewModelTest. The receiver's handler/api/state plumbing is `internal` to this
// package on purpose — the seam exists so the axis lives in exactly one place.

/**
 * ADR-0022 409 axis — the mutation conflicted: settle its state (Loading → Idle without
 * Success = the screen's "silent exit"), raise the notice, then re-fetch the detail so the
 * screen renders the fresh payload (the user's edit didn't win).
 */
internal suspend fun <T> RemittanceViewModel.reloadDetailAfterConflict(
    state: MutableStateFlow<UiState<T>>,
    remittanceId: String,
): Boolean {
    state.value = UiState.Idle
    detailChangedNoticeState.value = true
    loadRemittance(remittanceId, resetNotice = false)
    return true
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
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        lineResultState.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(lineResultState, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
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
                    when (response.status) {
                        HttpStatusCode.Forbidden -> {
                            deleteLineResultState.value = UiState.Idle
                            true
                        }

                        HttpStatusCode.Conflict -> {
                            reloadDetailAfterConflict(deleteLineResultState, remittanceId)
                        }

                        else -> {
                            false
                        }
                    }
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
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        dayBreakdownResultState.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(dayBreakdownResultState, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
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
                    when (response.status) {
                        HttpStatusCode.Forbidden -> {
                            dayBreakdownDeleteResultState.value = UiState.Idle
                            true
                        }

                        HttpStatusCode.Conflict -> {
                            reloadDetailAfterConflict(dayBreakdownDeleteResultState, remittanceId)
                        }

                        else -> {
                            false
                        }
                    }
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
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        submitResultState.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(submitResultState, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
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
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        undoResultState.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(undoResultState, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
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
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        headerUpdateResultState.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(headerUpdateResultState, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
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
