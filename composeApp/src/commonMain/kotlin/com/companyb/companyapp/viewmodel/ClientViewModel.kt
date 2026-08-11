package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.ClientState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClientViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ClientVM")

    private val _searchResults = MutableStateFlow<UiState<List<ClientResponse>>>(UiState.Idle)
    val searchResults: StateFlow<UiState<List<ClientResponse>>> = _searchResults.asStateFlow()

    private val _clientDetail = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val clientDetail: StateFlow<UiState<ClientResponse>> = _clientDetail.asStateFlow()

    private val _updateClientState = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val updateClientState: StateFlow<UiState<ClientResponse>> = _updateClientState.asStateFlow()

    private val _anonymizeState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val anonymizeState: StateFlow<UiState<Unit>> = _anonymizeState.asStateFlow()

    // D4 — 409 "changed elsewhere" indication: the PATCH conflicted, the detail was reloaded,
    // and the screen shows a one-shot notice so the user knows their edit didn't win.
    private val _detailChangedNotice = MutableStateFlow(false)
    val detailChangedNotice: StateFlow<Boolean> = _detailChangedNotice.asStateFlow()

    // D2 — debounce + current-query guard. `onQueryChange` is the rebuild entry point (debounce
    // lives in the VM so it's testable via virtual time); `retrySearch` refires the latest query
    // through the SAME tracked job so typing or X-clearing also cancels a retry in flight (an
    // untracked retry could otherwise resurrect results under a cleared/newer query).
    private var searchJob: Job? = null
    private var latestQuery: String = ""
    private var detailJob: Job? = null

    fun onQueryChange(query: String) {
        latestQuery = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) {
            _searchResults.value = UiState.Idle
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                launchSearch(trimmed, this)
            }
    }

    fun retrySearch() {
        val trimmed = latestQuery.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) return
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                launchSearch(trimmed, this)
            }
    }

    // D2 current-query guard, implemented as structured cancellation: the request launches as a
    // CHILD of the caller's job (handler scope override), so any newer keystroke, X-clear, or
    // retry cancels the in-flight request. The stale response dies at the cancellation
    // (ApiCallHandler rethrows CancellationException instead of surfacing an Error), so only the
    // latest query's response can ever commit — out-of-order writes are structurally impossible.
    private fun launchSearch(
        query: String,
        scope: CoroutineScope,
    ) {
        handler.launch(
            scope = scope,
            state = _searchResults,
            operation = "search",
            endpoint = "GET /api/clients",
            entryMessage = "search called: query=$query",
            block = {
                apiClient.httpClient.get("/api/clients") {
                    parameter("q", query)
                }
            },
            transform = { it.body() },
        )
    }

    fun loadClient(
        clientId: String,
        resetNotice: Boolean = true,
    ) {
        // Reset the changed-elsewhere notice on entry loads; the 409 conflict-reload passes
        // resetNotice = false so the notice it just raised survives the re-fetch.
        if (resetNotice) {
            _detailChangedNotice.value = false
        }
        // Tracked so a PATCH commit can cancel an in-flight reload (see updateClient): the detail
        // flow has two writers, and a stale GET landing after a fresher PATCH commit would
        // revert the display to pre-edit data.
        detailJob?.cancel()
        detailJob =
            handler.launch(
                state = _clientDetail,
                operation = "loadClient",
                endpoint = "GET /api/clients/$clientId",
                entryMessage = "loadClient called: clientId=$clientId",
                block = { apiClient.httpClient.get("/api/clients/$clientId") },
                transform = { it.body() },
            )
    }

    fun updateClient(
        clientId: String,
        request: UpdateClientRequest,
    ) {
        handler.launch(
            state = _updateClientState,
            operation = "updateClient",
            endpoint = "PATCH /api/clients/$clientId",
            entryMessage = "updateClient called: clientId=$clientId",
            block = {
                apiClient.httpClient.patch("/api/clients/$clientId") {
                    setBody(request)
                }
            },
            // D4 — the PATCH response IS the updated record: commit it straight into the detail
            // flow so the display shows the new value when edit mode exits (the screen renders
            // clientDetail, not updateClientState — without this the edit would look lost). The
            // reload-cancel keeps the commit from being overwritten by a stale in-flight GET.
            transform = { response ->
                detailJob?.cancel()
                val updated = response.body<ClientResponse>()
                _clientDetail.value = UiState.Success(updated)
                updated
            },
            // D4 — pessimistic per-field edit axes:
            // 403 = capability revoked mid-session → silent exit (no error, no inline message;
            //      backend-authoritative, ADR-0007 — the screen stays on stale data until reload).
            // 409 = changed elsewhere → reload + changed-fields indication; the fresh payload
            //      replaces the value the user was editing (their edit didn't win).
            // 404 = the record is gone (anonymized elsewhere) → reload renders the husk (D10).
            // Any other non-success (400 blank names / BP pair / validation) → generic Error →
            //      screen shows the inline error + stays in edit mode.
            onNonSuccess = { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        _updateClientState.value = UiState.Idle
                        true
                    }

                    HttpStatusCode.Conflict, HttpStatusCode.NotFound -> {
                        _updateClientState.value = UiState.Idle
                        if (response.status == HttpStatusCode.Conflict) {
                            _detailChangedNotice.value = true
                        }
                        loadClient(clientId, resetNotice = false)
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
        )
    }

    fun anonymizeClient(clientId: String) {
        handler.launch(
            state = _anonymizeState,
            operation = "anonymizeClient",
            endpoint = "POST /api/clients/$clientId/anonymize",
            entryMessage = "anonymizeClient called: clientId=$clientId",
            block = { apiClient.httpClient.post("/api/clients/$clientId/anonymize") },
            // 204 no body — transform runs only on success; the notice crosses the VM boundary to
            // the search screen's snackbar via ClientState (D1; the detail entry's VM is a
            // different instance than the search entry's — see ClientState doc comment).
            transform = {
                ClientState.setAnonymizeNotice("Client anonymized")
                Unit
            },
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_SEARCH_CHARS = 2
    }
}
