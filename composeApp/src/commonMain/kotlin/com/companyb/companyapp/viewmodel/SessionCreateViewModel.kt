package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AddSessionConcernRequest
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.dto.SessionPreviewResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #348 — SessionCreate flow: find the client (debounced search, the ClientViewModel D2 port),
 * create one on the spot via [ClientCreateDialog] when search comes up empty, show the
 * server-computed session type + defaulted base price (the #348 preview read — never replicated
 * client-side), pick concerns, and submit the PENDING walk-in create.
 *
 * The branch is fixed at entry (constructor) from SessionState — the same branch context every
 * other entry-scoped screen bakes in.
 */
@OptIn(ExperimentalUuidApi::class)
class SessionCreateViewModel(
    private val apiClient: ApiClient,
    private val branchId: String,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "SessionCreateVM")

    // --- Client picker: keep-last debounced search (the D2/#162 port, entry-scoped). ---
    private val keptResults = KeepLast<List<ClientResponse>>(viewModelScope)
    val searchResults: StateFlow<UiState<List<ClientResponse>>> = keptResults.state
    val freshestResults: StateFlow<List<ClientResponse>?> = keptResults.freshest

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null
    private var latestQuery: String = ""

    fun onQueryChange(query: String) {
        _query.value = query
        latestQuery = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) {
            keptResults.stateFlow.value = UiState.Idle
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                launchSearch(trimmed, this, "search called: query=$trimmed")
            }
    }

    fun retrySearch() {
        val trimmed = latestQuery.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) return
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                launchSearch(trimmed, this, "search retried: query=$trimmed")
            }
    }

    // Structured cancellation (the ClientViewModel D2 guard): each request is a child of the
    // caller's job, so a newer keystroke cancels the in-flight one and only the latest commits.
    private fun launchSearch(
        query: String,
        scope: CoroutineScope,
        entryMessage: String,
    ) {
        handler.launch(
            scope = scope,
            state = keptResults.stateFlow,
            operation = "search",
            endpoint = "GET /api/clients",
            entryMessage = entryMessage,
            block = {
                apiClient.httpClient.get(ApiRoutes.CLIENTS) { parameter("q", query) }
            },
            transform = { it.body() },
        )
    }

    // --- Selected client + preview ---

    private val _selectedClient = MutableStateFlow<ClientResponse?>(null)
    val selectedClient: StateFlow<ClientResponse?> = _selectedClient.asStateFlow()

    private val _preview = MutableStateFlow<UiState<SessionPreviewResponse>>(UiState.Idle)
    val preview: StateFlow<UiState<SessionPreviewResponse>> = _preview.asStateFlow()

    /** Selects a picker hit or a just-created client; the preview drives type + price display. */
    fun selectClient(client: ClientResponse) {
        _selectedClient.value = client
        loadPreview(client.id)
    }

    fun retryPreview() {
        _selectedClient.value?.let { loadPreview(it.id) }
    }

    /** #348 — the picker's "Change" action: selection dropped, preview back to Idle. */
    fun clearSelectedClient() {
        _selectedClient.value = null
        _preview.value = UiState.Idle
    }

    private fun loadPreview(clientId: String) {
        if (_preview.value is UiState.Loading) return
        _preview.value = UiState.Loading
        handler.launch(
            state = _preview,
            operation = "loadPreview",
            endpoint = "GET /api/branches/$branchId/session-preview",
            block = {
                apiClient.httpClient.get(ApiRoutes.branchSessionPreview(branchId)) {
                    parameter("clientId", clientId)
                }
            },
            transform = { it.body() },
        )
    }

    // --- Concern multi-select ---

    private val _concerns = MutableStateFlow<UiState<List<ConcernResponse>>>(UiState.Idle)
    val concerns: StateFlow<UiState<List<ConcernResponse>>> = _concerns.asStateFlow()

    // Retry-loop lesson: effects keyed on this state fire loads on Idle only; manual retry on Error.
    fun loadConcerns() {
        if (_concerns.value !is UiState.Idle) return
        _concerns.value = UiState.Loading
        handler.launch(
            state = _concerns,
            operation = "loadConcerns",
            endpoint = "GET /api/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
            transform = { it.body() },
        )
    }

    fun retryConcerns() {
        if (_concerns.value is UiState.Loading) return
        _concerns.value = UiState.Loading
        handler.launch(
            state = _concerns,
            operation = "retryConcerns",
            endpoint = "GET /api/concerns",
            block = { apiClient.httpClient.get(ApiRoutes.CONCERNS) },
            transform = { it.body() },
        )
    }

    private val _selectedConcernIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedConcernIds: StateFlow<Set<String>> = _selectedConcernIds.asStateFlow()

    fun toggleConcern(concernId: String) {
        _selectedConcernIds.update { selected ->
            if (concernId in selected) selected - concernId else selected + concernId
        }
    }

    // --- Submit ---

    private val _createResult = MutableStateFlow<UiState<SessionResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<SessionResponse>> = _createResult.asStateFlow()

    /**
     * Concern ids whose POST failed after the session itself was created. The session EXISTS at
     * that point — navigation proceeds and the detail screen shows server truth; failures are
     * surfaced inline rather than pretending the whole submit failed.
     */
    private val _concernAddFailures = MutableStateFlow(0)
    val concernAddFailures: StateFlow<Int> = _concernAddFailures.asStateFlow()

    fun createSession(
        finalPrice: String,
        remarks: String?,
        otherConcerns: String?,
    ) {
        val client = _selectedClient.value ?: return
        if (_createResult.value is UiState.Loading) return
        _createResult.value = UiState.Loading
        handler.launch(
            state = _createResult,
            operation = "createSession",
            endpoint = "POST /api/sessions",
            block = {
                apiClient.httpClient.post(ApiRoutes.SESSIONS) {
                    setBody(
                        CreateSessionRequest(
                            id = Uuid.random().toString(),
                            clientId = client.id,
                            branchId = branchId,
                            // Walk-in vs booked are identical once started (BR §129); no booking
                            // UI exists in this flow, so sessions start as walk-ins.
                            isWalkIn = true,
                            finalPrice = finalPrice,
                            remarks = remarks?.trim()?.ifBlank { null },
                            otherConcerns = otherConcerns?.trim()?.ifBlank { null },
                        ),
                    )
                }
            },
            transform = { response ->
                val session = response.body<SessionResponse>()
                addSelectedConcerns(session.id)
                session
            },
            onNonSuccess = { response ->
                // 409 carries the one-active-session rule's message; 400/403 surface inline too.
                val detail =
                    extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                _createResult.value =
                    UiState.Error(detail ?: "Create session failed: ${response.status.value}")
                true
            },
        )
    }

    private suspend fun addSelectedConcerns(sessionId: String) {
        val ids = _selectedConcernIds.value
        if (ids.isEmpty()) return
        var failures = 0
        ids.forEach { concernId ->
            runCatching {
                val response =
                    apiClient.httpClient.post(ApiRoutes.sessionConcerns(sessionId)) {
                        setBody(AddSessionConcernRequest(concernId = concernId))
                    }
                if (!response.status.isSuccess()) failures++
            }.onFailure {
                failures++
            }
        }
        _concernAddFailures.value = failures
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_SEARCH_CHARS = 2
    }
}
