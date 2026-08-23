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

    /**
     * #382 — authoritative reload after a mutation or a conflict: re-fetches regardless of
     * how the entry was seeded (unlike [retry], which is notifications-path-only). The
     * pessimistic model (ADR-0022) never commits local edits, so server truth is simply
     * what this lands with. Concurrent reloads are blocked by [inFlight].
     */
    fun refresh() {
        fetchStarted = true
        fetch()
    }

    private fun fetch() {
        if (inFlight) return
        inFlight = true
        // #382 — a refresh failure must not destroy a rendered detail: the bearer-only read
        // 404s for seeded dashboard-push rows whose caller holds no notification, and a
        // transport blip mid-reload means "no fresh data", not "the session is gone". Keep
        // the last good row on those legs (mutation errors already surface inline via the
        // pane's result flows); only a failed INITIAL load lands in Error.
        val lastGood = (_detail.value as? UiState.Success)?.data
        handler
            .launch(
                state = _detail,
                operation = "loadDetail",
                endpoint = "GET /api/sessions/$sessionId",
                block = { apiClient.httpClient.get(ApiRoutes.session(sessionId)) },
                transform = { it.body() },
                // Status-leg failures (the bearer-only 404 for seeded dashboard-push rows
                // whose caller holds no notification, a revoked capability 403) keep the
                // rendered row; only a failed INITIAL load lands in Error.
                onNonSuccess = {
                    if (lastGood != null) {
                        _detail.value = UiState.Success(lastGood)
                        true
                    } else {
                        false
                    }
                },
            ).invokeOnCompletion { inFlight = false }
    }
}
