package com.companyb.companyapp.remittance
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.KeepLastByKey
import com.companyb.companyapp.async.LatestLoad
import com.companyb.companyapp.async.LaunchHooks
import com.companyb.companyapp.async.LaunchRequest
import com.companyb.companyapp.async.LoadGeneration
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.remittance.AddDayBreakdownRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceDraftRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceLineRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
            LaunchRequest(
                state = _remittanceList,
                operation = "loadRemittances",
                endpoint = "GET /api/remittances",
                entryMessage = "loadRemittances called: branchId=$branchId status=$status",
                block = {
                    apiClient.httpClient.get(ApiRoutes.REMITTANCES) {
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
            ),
        )
    }

    // D2 — create popup.
    private val _createDraftResult = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val createDraftResult: StateFlow<UiState<RemittanceResponse>> = _createDraftResult.asStateFlow()

    // D6 — detail (the receipt), includes the #118 snapshot block when present.
    private val _remittanceDetail = MutableStateFlow<UiState<RemittanceDetailResponse>>(UiState.Idle)
    val remittanceDetail: StateFlow<UiState<RemittanceDetailResponse>> = _remittanceDetail.asStateFlow()

    // #484 — latest-wins guard for the detail surface (#611 LoadGeneration owner, keptByTab
    // shape): a landing superseded by a newer load never commits. A double-initial overlap
    // whose stale landing arrives first simply holds Loading until the superseding load lands.
    private val detailGuard = LoadGeneration()

    // #490 — per-source picker guards + range capture (#611): the three picker caches share one
    // range marker, so a stale landing must commit neither entries nor marker. The guard closes
    // same-source ordering (same-range sibling loads never invalidate each other); the range
    // half of the commit check stays in onCommit below.
    private val sessionPickerGuard = LoadGeneration()
    private val productSalePickerGuard = LoadGeneration()
    private val dayPickerGuard = LoadGeneration()

    // Current detail range for the range half of the picker commit check.
    private fun currentDetailRange(): Pair<String, String>? =
        (remittanceDetail.value as? UiState.Success)?.data?.let {
            it.dateRangeStart to it.dateRangeEnd
        }

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

    // #483 — the range the picker caches were loaded for (all three pickers load the detail's
    // range together). Screens render a cached Success only when it matches the detail's
    // current range; a header range edit invalidates the caches until the reloads land, and
    // picker dialogs key their tick-selection on it so out-of-range selections never survive.
    private val _pickerLoadedRange = MutableStateFlow<Pair<String, String>?>(null)
    val pickerLoadedRange: StateFlow<Pair<String, String>?> = _pickerLoadedRange.asStateFlow()

    // D3 — line mutations (version-bumped server-side; no expectedVersion in the request body).
    private val lineResultState = MutableStateFlow<UiState<RemittanceLineResponse>>(UiState.Idle)
    val lineResult: StateFlow<UiState<RemittanceLineResponse>> = lineResultState.asStateFlow()

    private val deleteLineResultState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteLineResult: StateFlow<UiState<Unit>> = deleteLineResultState.asStateFlow()

    // D4 — day-breakdown mutations (#118 G5 DELETE).
    private val dayBreakdownResultState = MutableStateFlow<UiState<RemittanceDayBreakdownResponse>>(UiState.Idle)
    val dayBreakdownResult: StateFlow<UiState<RemittanceDayBreakdownResponse>> =
        dayBreakdownResultState.asStateFlow()

    private val dayBreakdownDeleteResultState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val dayBreakdownDeleteResult: StateFlow<UiState<Unit>> = dayBreakdownDeleteResultState.asStateFlow()

    // D5 — submit.
    private val submitResultState = MutableStateFlow<UiState<RemittanceSubmitResponse>>(UiState.Idle)
    val submitResult: StateFlow<UiState<RemittanceSubmitResponse>> = submitResultState.asStateFlow()

    // D10 — undo.
    private val undoResultState = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val undoResult: StateFlow<UiState<RemittanceResponse>> = undoResultState.asStateFlow()

    // D9 — header PATCH.
    private val headerUpdateResultState = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val headerUpdateResult: StateFlow<UiState<RemittanceResponse>> = headerUpdateResultState.asStateFlow()

    // D6 — drift (lazy: fetched on expander click only, cached after).
    private val driftState = MutableStateFlow<UiState<RemittanceDriftResponse>>(UiState.Idle)
    val drift: StateFlow<UiState<RemittanceDriftResponse>> = driftState.asStateFlow()

    // ADR-0022 409 axis — a mutation conflicted; the detail was reloaded and the screen shows a
    // one-shot notice so the user knows their edit didn't win.
    private val detailChangedNoticeState = MutableStateFlow(false)
    val detailChangedNotice: StateFlow<Boolean> = detailChangedNoticeState.asStateFlow()

    // D2 — idempotent create (server-side insertIgnore: a duplicate (branch, type, date) returns
    // the existing draft — the popup treats success as success and refreshes).
    fun createDraft(request: CreateRemittanceDraftRequest) {
        handler.launch(
            state = _createDraftResult,
            operation = "createDraft",
            endpoint = "POST /api/remittances",
            block = {
                apiClient.httpClient.post(ApiRoutes.REMITTANCES) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    // D6 — detail load; the 409 conflict-reload passes resetNotice = false so the notice it just
    // raised survives the re-fetch.
    //
    // #484 — latest-wins: a landing superseded by a newer load — an overlapping success-path
    // reload — drops its body without committing it, and a superseded failure writes no Error
    // (#176). No coalescing guard by design: a mutation-triggered reload must always dispatch
    // — skipping it would let the in-flight pre-mutation snapshot win (the loadSent precedent).
    // The decode stays pure (#490 — no marker write inside the suspend window, so a bump
    // mid-deserialization pollutes nothing; the handler's post-decode recheck owns the commit).
    fun loadRemittance(
        remittanceId: String,
        resetNotice: Boolean = true,
    ) {
        if (resetNotice) {
            detailChangedNoticeState.value = false
        }
        handler.launchLatest(
            LatestLoad(
                state = _remittanceDetail,
                operation = "loadRemittance",
                endpoint = "GET /api/remittances/$remittanceId",
                entryMessage = "loadRemittance called: remittanceId=$remittanceId",
                block = { apiClient.httpClient.get(ApiRoutes.remittance(remittanceId)) },
                decode = { it.body() },
                guard = detailGuard,
            ),
        )
    }

    // #484 — the single funnel for the 7 post-mutation detail refreshes (the line/day and
    // header/submit/undo success effects): identical to loadRemittance with defaults, so a
    // clean post-mutation reload resets the changed-elsewhere notice. Conflict reloads bypass
    // it (resetNotice = false preserves the notice they just raised).
    // #561 — widened from internal to public with the remittance owner: the detail screen
    // (same owner) drives post-mutation refreshes as intent commands per #560 precedent.
    fun reloadDetail(remittanceId: String) {
        loadRemittance(remittanceId)
    }

    // D3 — sessions-in-range picker (#118 G2; voided sessions excluded server-side).
    // #490 — per-source (guard, range) commit: a landing from another range (or superseded
    // by a newer load of the same source) commits neither entries nor the shared marker; a
    // superseded failure writes no Error over the current Success (#176).
    fun loadSessionPicker(
        branchId: String,
        from: String,
        to: String,
    ) {
        val capturedRange = from to to
        handler.launchLatest(
            LatestLoad(
                state = _sessionPicker,
                operation = "loadSessionPicker",
                endpoint = "GET /api/branches/$branchId/remittance-sessions",
                entryMessage = "loadSessionPicker called: branchId=$branchId from=$from to=$to",
                block = {
                    apiClient.httpClient.get(ApiRoutes.branchRemittanceSessions(branchId)) {
                        parameter("from", from)
                        parameter("to", to)
                    }
                },
                decode = { response -> response.body<List<RemittanceSessionPickerEntryResponse>>() },
                guard = sessionPickerGuard,
                onCommit = {
                    if (capturedRange == currentDetailRange()) {
                        _pickerLoadedRange.value = capturedRange
                    }
                },
            ),
        )
    }

    // D3 — product sales-in-range picker (#118 G3).
    fun loadProductSalePicker(
        branchId: String,
        from: String,
        to: String,
    ) {
        val capturedRange = from to to
        handler.launchLatest(
            LatestLoad(
                state = _productSalePicker,
                operation = "loadProductSalePicker",
                endpoint = "GET /api/branches/$branchId/remittance-product-sales",
                entryMessage = "loadProductSalePicker called: branchId=$branchId from=$from to=$to",
                block = {
                    apiClient.httpClient.get(ApiRoutes.branchRemittanceProductSales(branchId)) {
                        parameter("from", from)
                        parameter("to", to)
                    }
                },
                decode = { response -> response.body<List<RemittanceProductSalePickerEntryResponse>>() },
                guard = productSalePickerGuard,
                onCommit = {
                    if (capturedRange == currentDetailRange()) {
                        _pickerLoadedRange.value = capturedRange
                    }
                },
            ),
        )
    }

    // D4 — days-in-range picker with effective status (#118 G4).
    fun loadDayPicker(
        branchId: String,
        from: String,
        to: String,
    ) {
        val capturedRange = from to to
        handler.launchLatest(
            LatestLoad(
                state = _dayPicker,
                operation = "loadDayPicker",
                endpoint = "GET /api/branches/$branchId/remittance-days",
                entryMessage = "loadDayPicker called: branchId=$branchId from=$from to=$to",
                block = {
                    apiClient.httpClient.get(ApiRoutes.branchRemittanceDays(branchId)) {
                        parameter("from", from)
                        parameter("to", to)
                    }
                },
                decode = { response -> response.body<List<RemittanceDayPickerEntryResponse>>() },
                guard = dayPickerGuard,
                onCommit = {
                    if (capturedRange == currentDetailRange()) {
                        _pickerLoadedRange.value = capturedRange
                    }
                },
            ),
        )
    }

    // #561 — folded from RemittanceDetailMutations (#479 extension seam retired per #535):
    // every detail mutation shares ONE non-success axis (ADR-0022). Former extension
    // functions are now private-state members; intent commands stay public, mutable
    // backing stays private, and the terminal axis lives in exactly one place below.

    /**
     * ADR-0022 409 axis — the ONE terminal 403/409 path shared by all 7 detail-mutation adapters.
     * 403 settles the mutation's state (Loading → Idle without Success = the screen's "silent
     * exit"); 409 additionally raises the notice, then re-fetches the detail so the screen renders
     * the fresh payload (the user's edit didn't win). Returns true when handled (the handler then
     * skips its generic Error); false for any other status.
     */
    private suspend fun <T> handleDetailMutationTerminal(
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

    fun addLine(
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

    fun deleteLine(
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

    fun addDayBreakdown(
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
    fun deleteDayBreakdown(
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
    fun submit(
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
    fun undo(
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
    fun updateHeader(
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
    fun loadDrift(remittanceId: String) {
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
}
