package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.dto.UpdatePractitionerRemarksRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody

// #479 — the session roster + member-directory seam, extracted from SessionViewModel so the
// file-function wall (TMF) stays honest. The #382 generation discipline travels with it:
// #486 keys one VM per selection, so a switch starts a fresh scope; within a scope,
// rapid successive loads still race, and a superseded roster or member GET must not
// commit over the newer request (stale body never deserialized — the committed value
// stands). These are extension functions on the ViewModel: SessionDetailPane and
// SessionDetailPaneDialogs call sites resolve identically, with imports added at the top.

internal fun SessionViewModel.loadSessionPractitioners(sessionId: String) {
    // #382 — generation guard: the desktop pane reuses one VM per selection, so a
    // superseded roster GET (a newer load dispatched mid-flight) must not commit over the
    // newer request — a stale body is never deserialized; the committed value stands.
    ++rosterGeneration
    handler.launch(
        LaunchRequest(
            state = practitionersState,
            operation = "loadSessionPractitioners",
            endpoint = "GET /api/sessions/$sessionId/practitioners",
            block = { apiClient.httpClient.get(ApiRoutes.sessionPractitioners(sessionId)) },
            transform = { it.body() },
            stamp = { rosterGeneration },
            fallback = { (practitionersState.value as? UiState.Success)?.data ?: emptyList() },
        ),
    )
}

internal fun SessionViewModel.addPractitioner(
    sessionId: String,
    request: AddPractitionerRequest,
) {
    handler.launch(
        state = practitionerResultState,
        operation = "addPractitioner",
        endpoint = "POST /api/sessions/$sessionId/practitioners",
        block = {
            apiClient.httpClient.post(ApiRoutes.sessionPractitioners(sessionId)) {
                setBody(request)
            }
        },
        transform = { it.body() },
    )
}

internal fun SessionViewModel.updatePractitionerRemarks(
    sessionId: String,
    practitionerId: String,
    request: UpdatePractitionerRemarksRequest,
) {
    handler.launch(
        state = practitionerResultState,
        operation = "updatePractitionerRemarks",
        endpoint = "PATCH /api/sessions/$sessionId/practitioners/$practitionerId",
        block = {
            apiClient.httpClient.patch(
                ApiRoutes.sessionPractitioner(sessionId, practitionerId),
            ) {
                setBody(request)
            }
        },
        transform = { it.body() },
    )
}

internal fun SessionViewModel.removePractitioner(
    sessionId: String,
    practitionerId: String,
) {
    handler.launch(
        state = practitionerResultState,
        operation = "removePractitioner",
        endpoint = "DELETE /api/sessions/$sessionId/practitioners/$practitionerId",
        block = {
            apiClient.httpClient.delete(
                ApiRoutes.sessionPractitioner(sessionId, practitionerId),
            )
        },
        transform = {
            SessionPractitionerResponse(
                id = "",
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = null,
                slotAtTime = 0,
            )
        },
    )
}

/** #382 — member directory for the add-practitioner picker at the session's branch. */
internal fun SessionViewModel.loadBranchMembers(branchId: String) {
    if (branchMembersState.value is UiState.Loading) return
    branchMembersState.value = UiState.Loading
    // Same generation discipline as the roster: only the newest branch request commits.
    ++membersGeneration
    handler.launch(
        LaunchRequest(
            state = branchMembersState,
            operation = "loadBranchMembers",
            endpoint = "GET /api/branches/$branchId/members",
            block = { apiClient.httpClient.get(ApiRoutes.branchMembers(branchId)) },
            transform = { it.body() },
            stamp = { membersGeneration },
            fallback = { (branchMembersState.value as? UiState.Success)?.data ?: emptyList() },
        ),
    )
}

/** #382 — one-shot drain: the pane handles a terminal practitioner landing exactly once. */
internal fun SessionViewModel.consumePractitionerResult() {
    practitionerResultState.value = UiState.Idle
}
