package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceDriftResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.dto.UndoRemittanceRequest
import com.companyb.companyapp.dto.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The per-(branch, status) key shared by the keep-last mirror and the screen's render gate. */
fun remittanceListKey(
    branchId: String,
    status: String,
): String = "$branchId:$status"

/**
 * #120 — Remittance screen state per the locked #103 D1–D10, consuming the #118 read-backs and
 * #119 undo/PATCH endpoints.
 *
 * Mutation axes (ADR-0022, #113 D4 precedent):
 * - 403 = capability revoked mid-session → silent exit (state Idle without Success, no error).
 * - 409 = version conflict (submit/undo/PATCH take `expectedVersion` from the detail; line/day
 *   mutations bump the version server-side so a concurrent coordinator's edit races them too) →
 *   reload the detail + raise the changed-elsewhere notice (the fresh payload wins).
 * - Any other non-success → generic Error surfaced by the screen.
 */
class RemittanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "RemittanceVM")

    // D1 — list (status-filtered, one tab at a time).
    private val _remittanceList = MutableStateFlow<UiState<List<RemittanceResponse>>>(UiState.Idle)
    val remittanceList: StateFlow<UiState<List<RemittanceResponse>>> = _remittanceList.asStateFlow()

    // Keep-last per tab + per-key in-flight guard, VM-side (the #161 port shape, unified by
    // #162 — KeepLastByKey): the last successful list per (branchId, status) survives
    // Loading/Error and composition re-entry (the old screen-side remember cache died on
    // pop-back). Keyed by branch defensively — the list route takes no branch arg (picked via
    // the accessible-branch picker); in practice a branch change re-enters the route with a
    // fresh VM, but the key keeps the mirror honest either way. Written ONLY by commit() at
    // the list transform; the screen's render gate keys on the SELECTED tab's mirror, so a
    // cross-tab response can never render foreign rows (a fetch landing after a tab switch
    // previously last-writer-won on the single list state).
    private val keptByTab = KeepLastByKey<String, List<RemittanceResponse>>()
    val lastByTab: StateFlow<Map<String, List<RemittanceResponse>>> = keptByTab.lastByKey

    // D1 — list, status-filtered (DRAFT/SUBMITTED/ALL).
    fun loadRemittances(
        branchId: String,
        status: String,
    ) {
        val key = remittanceListKey(branchId, status)
        // Synchronous per-key guard (the #143 in-flight shape): the handler's Loading
        // assignment is not guaranteed to land before launch returns, so a state-based guard
        // would race a same-frame double-tap — the entry + tab effects both fire the default
        // tab's load on first composition. Per-key, NOT a single slot: a tab switch while
        // another tab's load is in flight must not skip the new tab's fetch. Cleared on every
        // non-cancellation handler exit path (commit / non-success / exception) so no key can
        // wedge; cancellation only happens at VM teardown, where the guard dies with the VM
        // (the handler rethrows CancellationException without invoking onError).
        if (!keptByTab.tryBegin(key)) return
        handler.launch(
            state = _remittanceList,
            operation = "loadRemittances",
            endpoint = "GET /api/remittances",
            entryMessage = "loadRemittances called: branchId=$branchId status=$status",
            block = {
                apiClient.httpClient.get("/api/remittances") {
                    parameter("branchId", branchId)
                    parameter("status", status)
                }
            },
            transform = { response ->
                val body = response.body<List<RemittanceResponse>>()
                keptByTab.commit(key, body)
                body
            },
            onNonSuccess = {
                keptByTab.finish(key)
                false
            },
            onError = {
                keptByTab.finish(key)
            },
        )
    }

    // D2 — create popup.
    private val _createDraftResult = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val createDraftResult: StateFlow<UiState<RemittanceResponse>> = _createDraftResult.asStateFlow()

    // D6 — detail (the receipt), includes the #118 snapshot block when present.
    private val _remittanceDetail = MutableStateFlow<UiState<RemittanceDetailResponse>>(UiState.Idle)
    val remittanceDetail: StateFlow<UiState<RemittanceDetailResponse>> = _remittanceDetail.asStateFlow()

    // D3/D4 — picker sources (#118 G2/G3/G4), loaded on dialog open and cached.
    private val _sessionPicker =
        MutableStateFlow<UiState<List<RemittanceSessionPickerEntryResponse>>>(UiState.Idle)
    val sessionPicker: StateFlow<UiState<List<RemittanceSessionPickerEntryResponse>>> =
        _sessionPicker.asStateFlow()

    private val _productSalePicker =
        MutableStateFlow<UiState<List<RemittanceProductSalePickerEntryResponse>>>(UiState.Idle)
    val productSalePicker: StateFlow<UiState<List<RemittanceProductSalePickerEntryResponse>>> =
        _productSalePicker.asStateFlow()

    private val _dayPicker =
        MutableStateFlow<UiState<List<RemittanceDayPickerEntryResponse>>>(UiState.Idle)
    val dayPicker: StateFlow<UiState<List<RemittanceDayPickerEntryResponse>>> = _dayPicker.asStateFlow()

    // D3 — line mutations (version-bumped server-side; no expectedVersion in the request body).
    private val _lineResult = MutableStateFlow<UiState<RemittanceLineResponse>>(UiState.Idle)
    val lineResult: StateFlow<UiState<RemittanceLineResponse>> = _lineResult.asStateFlow()

    private val _deleteLineResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteLineResult: StateFlow<UiState<Unit>> = _deleteLineResult.asStateFlow()

    // D4 — day-breakdown mutations (#118 G5 DELETE).
    private val _dayBreakdownResult = MutableStateFlow<UiState<RemittanceDayBreakdownResponse>>(UiState.Idle)
    val dayBreakdownResult: StateFlow<UiState<RemittanceDayBreakdownResponse>> =
        _dayBreakdownResult.asStateFlow()

    private val _dayBreakdownDeleteResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val dayBreakdownDeleteResult: StateFlow<UiState<Unit>> = _dayBreakdownDeleteResult.asStateFlow()

    // D5 — submit.
    private val _submitResult = MutableStateFlow<UiState<RemittanceSubmitResponse>>(UiState.Idle)
    val submitResult: StateFlow<UiState<RemittanceSubmitResponse>> = _submitResult.asStateFlow()

    // D10 — undo.
    private val _undoResult = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val undoResult: StateFlow<UiState<RemittanceResponse>> = _undoResult.asStateFlow()

    // D9 — header PATCH.
    private val _headerUpdateResult = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val headerUpdateResult: StateFlow<UiState<RemittanceResponse>> = _headerUpdateResult.asStateFlow()

    // D6 — drift (lazy: fetched on expander click only, cached after).
    private val _drift = MutableStateFlow<UiState<RemittanceDriftResponse>>(UiState.Idle)
    val drift: StateFlow<UiState<RemittanceDriftResponse>> = _drift.asStateFlow()

    // ADR-0022 409 axis — a mutation conflicted; the detail was reloaded and the screen shows a
    // one-shot notice so the user knows their edit didn't win.
    private val _detailChangedNotice = MutableStateFlow(false)
    val detailChangedNotice: StateFlow<Boolean> = _detailChangedNotice.asStateFlow()

    // D2 — idempotent create (server-side insertIgnore: a duplicate (branch, type, date) returns
    // the existing draft — the popup treats success as success and refreshes).
    fun createDraft(request: CreateRemittanceDraftRequest) {
        handler.launch(
            state = _createDraftResult,
            operation = "createDraft",
            endpoint = "POST /api/remittances",
            block = {
                apiClient.httpClient.post("/api/remittances") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    // D6 — detail load; the 409 conflict-reload passes resetNotice = false so the notice it just
    // raised survives the re-fetch.
    fun loadRemittance(
        remittanceId: String,
        resetNotice: Boolean = true,
    ) {
        if (resetNotice) {
            _detailChangedNotice.value = false
        }
        handler.launch(
            state = _remittanceDetail,
            operation = "loadRemittance",
            endpoint = "GET /api/remittances/$remittanceId",
            entryMessage = "loadRemittance called: remittanceId=$remittanceId",
            block = { apiClient.httpClient.get("/api/remittances/$remittanceId") },
            transform = { it.body() },
        )
    }

    // D3 — sessions-in-range picker (#118 G2; voided sessions excluded server-side).
    fun loadSessionPicker(
        branchId: String,
        from: String,
        to: String,
    ) {
        handler.launch(
            state = _sessionPicker,
            operation = "loadSessionPicker",
            endpoint = "GET /api/branches/$branchId/remittance-sessions",
            entryMessage = "loadSessionPicker called: branchId=$branchId from=$from to=$to",
            block = {
                apiClient.httpClient.get("/api/branches/$branchId/remittance-sessions") {
                    parameter("from", from)
                    parameter("to", to)
                }
            },
            transform = { it.body() },
        )
    }

    // D3 — product sales-in-range picker (#118 G3).
    fun loadProductSalePicker(
        branchId: String,
        from: String,
        to: String,
    ) {
        handler.launch(
            state = _productSalePicker,
            operation = "loadProductSalePicker",
            endpoint = "GET /api/branches/$branchId/remittance-product-sales",
            entryMessage = "loadProductSalePicker called: branchId=$branchId from=$from to=$to",
            block = {
                apiClient.httpClient.get("/api/branches/$branchId/remittance-product-sales") {
                    parameter("from", from)
                    parameter("to", to)
                }
            },
            transform = { it.body() },
        )
    }

    // D4 — days-in-range picker with effective status (#118 G4).
    fun loadDayPicker(
        branchId: String,
        from: String,
        to: String,
    ) {
        handler.launch(
            state = _dayPicker,
            operation = "loadDayPicker",
            endpoint = "GET /api/branches/$branchId/remittance-days",
            entryMessage = "loadDayPicker called: branchId=$branchId from=$from to=$to",
            block = {
                apiClient.httpClient.get("/api/branches/$branchId/remittance-days") {
                    parameter("from", from)
                    parameter("to", to)
                }
            },
            transform = { it.body() },
        )
    }

    fun addLine(
        remittanceId: String,
        request: CreateRemittanceLineRequest,
    ) {
        handler.launch(
            state = _lineResult,
            operation = "addLine",
            endpoint = "POST /api/remittances/$remittanceId/lines",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/lines",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _lineResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_lineResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    fun deleteLine(
        remittanceId: String,
        lineId: String,
    ) {
        handler.launchUnit(
            state = _deleteLineResult,
            operation = "deleteLine",
            endpoint = "DELETE /api/remittances/$remittanceId/lines/$lineId",
            block = {
                apiClient.httpClient.delete(
                    "/api/remittances/$remittanceId/lines/$lineId",
                )
            },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _deleteLineResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_deleteLineResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    fun addDayBreakdown(
        remittanceId: String,
        request: AddDayBreakdownRequest,
    ) {
        handler.launch(
            state = _dayBreakdownResult,
            operation = "addDayBreakdown",
            endpoint = "POST /api/remittances/$remittanceId/day-breakdowns",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/day-breakdowns",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _dayBreakdownResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_dayBreakdownResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    // D4 — day-breakdown remove (#118 G5).
    fun deleteDayBreakdown(
        remittanceId: String,
        breakdownId: String,
    ) {
        handler.launchUnit(
            state = _dayBreakdownDeleteResult,
            operation = "deleteDayBreakdown",
            endpoint = "DELETE /api/remittances/$remittanceId/day-breakdowns/$breakdownId",
            block = {
                apiClient.httpClient.delete(
                    "/api/remittances/$remittanceId/day-breakdowns/$breakdownId",
                )
            },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _dayBreakdownDeleteResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_dayBreakdownDeleteResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    // D5 — submit (version-locked: expectedVersion comes from the detail).
    fun submit(
        remittanceId: String,
        request: SubmitRemittanceRequest,
    ) {
        handler.launch(
            state = _submitResult,
            operation = "submit",
            endpoint = "POST /api/remittances/$remittanceId/submit",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/submit",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _submitResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_submitResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    // D10 — undo within 48h (reason required; version-locked).
    fun undo(
        remittanceId: String,
        request: UndoRemittanceRequest,
    ) {
        handler.launch(
            state = _undoResult,
            operation = "undo",
            endpoint = "POST /api/remittances/$remittanceId/undo",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/undo",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _undoResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_undoResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    // D9 — header PATCH (draft-only, version-locked).
    fun updateHeader(
        remittanceId: String,
        request: UpdateRemittanceHeaderRequest,
    ) {
        handler.launch(
            state = _headerUpdateResult,
            operation = "updateHeader",
            endpoint = "PATCH /api/remittances/$remittanceId",
            entryMessage = "updateHeader called: remittanceId=$remittanceId",
            block = {
                apiClient.httpClient.patch("/api/remittances/$remittanceId") {
                    setBody(request)
                }
            },
            transform = { it.body() },
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _headerUpdateResult.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict -> {
                        reloadDetailAfterConflict(_headerUpdateResult, remittanceId)
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    // D6 — drift (frozen vs current), lazy: fetched on expander click only, cached after.
    fun loadDrift(remittanceId: String) {
        handler.launch(
            state = _drift,
            operation = "loadDrift",
            endpoint = "GET /api/remittances/$remittanceId/drift",
            entryMessage = "loadDrift called: remittanceId=$remittanceId",
            block = { apiClient.httpClient.get("/api/remittances/$remittanceId/drift") },
            transform = { it.body() },
        )
    }

    // ADR-0022 409 axis — the mutation conflicted: settle its state (Loading → Idle without
    // Success = the screen's "silent exit"), raise the notice, then re-fetch the detail so the
    // screen renders the fresh payload (the user's edit didn't win).
    private suspend fun <T> reloadDetailAfterConflict(
        state: MutableStateFlow<UiState<T>>,
        remittanceId: String,
    ): Boolean {
        state.value = UiState.Idle
        _detailChangedNotice.value = true
        loadRemittance(remittanceId, resetNotice = false)
        return true
    }
}
