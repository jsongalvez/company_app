package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.PromoteConcernRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

// #479 — the session-concern seam, extracted from SessionViewModel so the file-function wall
// (TMF) stays honest. Extension functions on the ViewModel: SessionDetailPane call sites
// resolve identically, with imports added at the top.

internal fun SessionViewModel.loadAllConcerns() {
    handler.launch(
        state = concernsState,
        operation = "loadAllConcerns",
        endpoint = "GET /api/concerns",
        block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
        transform = { it.body() },
    )
}

internal fun SessionViewModel.loadSessionConcerns(sessionId: String) {
    handler.launch(
        state = sessionConcernsState,
        operation = "loadSessionConcerns",
        endpoint = "GET /api/sessions/$sessionId/concerns",
        block = { apiClient.httpClient.get(ApiRoutes.sessionConcerns(sessionId)) },
        transform = { it.body() },
    )
}

internal fun SessionViewModel.addSessionConcern(
    sessionId: String,
    request: AddSessionConcernRequest,
) {
    handler.launchUnit(
        state = concernResultState,
        operation = "addSessionConcern",
        endpoint = "POST /api/sessions/$sessionId/concerns",
        block = {
            apiClient.httpClient.post(ApiRoutes.sessionConcerns(sessionId)) {
                setBody(request)
            }
        },
    )
}

internal fun SessionViewModel.removeSessionConcern(
    sessionId: String,
    concernId: String,
) {
    handler.launchUnit(
        state = concernResultState,
        operation = "removeSessionConcern",
        endpoint = "DELETE /api/sessions/$sessionId/concerns/$concernId",
        block = {
            apiClient.httpClient.delete(
                ApiRoutes.sessionConcern(sessionId, concernId),
            )
        },
    )
}

internal fun SessionViewModel.promoteConcern(
    sessionId: String,
    request: PromoteConcernRequest,
) {
    handler.launchUnit(
        state = concernResultState,
        operation = "promoteConcern",
        endpoint = "POST /api/sessions/$sessionId/promote-concern",
        block = {
            apiClient.httpClient.post(
                ApiRoutes.sessionPromoteConcern(sessionId),
            ) {
                setBody(request)
            }
        },
    )
}

/** #382 — one-shot drain for concern command landings (add/remove/promote). */
internal fun SessionViewModel.consumeConcernResult() {
    concernResultState.value = UiState.Idle
}
