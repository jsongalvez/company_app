package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #152 — SessionDetail route VM (notifications-path data fetch, #151 Q7).
 *
 * One-shot fetch semantics: the fetch fires ONLY when the route arrived without a row
 * (`initialRow == null` — the Notifications entry point). The dashboard path passes the
 * enriched row and never dispatches a request (zero extra requests on the dashboard, the
 * user's perf concern).
 *
 * Entry-scoped via `viewModel { ... }` at the NavHost call site (#112 pattern): a fresh VM
 * per pushed entry, so the one-shot state self-cleans on pop. [loadIfNeeded] is additionally
 * guarded against double-fire — rotation/recomposition re-runs the screen's
 * LaunchedEffect while the entry-scoped VM survives, and the synchronous [fetchStarted] flag
 * must hold from the caller's frame (the #135 double-tap pattern).
 */
class SessionDetailViewModel(
    private val apiClient: ApiClient,
    private val sessionId: String,
    initialRow: DashboardSessionResponse?,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionDetailVM")

    // Constructor params without val/var aren't visible in member functions — capture the
    // entry row for [loadIfNeeded]'s one-shot gate.
    private val seededRow = initialRow

    private val _detail =
        MutableStateFlow<UiState<DashboardSessionResponse>>(
            initialRow?.let { UiState.Success(it) } ?: UiState.Loading,
        )
    val detail: StateFlow<UiState<DashboardSessionResponse>> = _detail.asStateFlow()

    private var fetchStarted = false

    // In-flight guard: ApiCallHandler launches are concurrent — two rapid retries would race
    // two GETs whose responses can land out of order (the stale-response overwrite class). The
    // flag is set synchronously before dispatch and cleared on completion. Volatile because
    // invokeOnCompletion runs in the completing job's context, not the caller's frame.
    @Volatile
    private var inFlight = false

    /**
     * Fetches once, and only when the entry carried no row. No-ops on the dashboard path
     * (row present) and on recomposition refires (entry-scoped VM + [fetchStarted]).
     */
    fun loadIfNeeded() {
        if (seededRow != null || fetchStarted) return
        fetchStarted = true
        fetch()
    }

    /**
     * Explicit retry from the error state — which is only reachable when the entry carried no
     * row (the seeded dashboard path sits in Success forever, so its no-op guard below never
     * fires in the UI). Concurrent retries are blocked by [inFlight].
     */
    fun retry() {
        if (seededRow != null) return
        fetch()
    }

    private fun fetch() {
        if (inFlight) return
        inFlight = true
        handler
            .launch(
                state = _detail,
                operation = "loadDetail",
                endpoint = "GET /api/sessions/$sessionId",
                block = { apiClient.httpClient.get(ApiRoutes.session(sessionId)) },
                transform = { it.body() },
            ).invokeOnCompletion { inFlight = false }
    }
}
