package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.async.ApiCallHandler
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.async.KeepLast
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.async.mutateRemoved
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Entry-scoped client search policy shared by Clients, product-sale, and session-create. */
internal class ClientSearcher(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
    tag: String,
) {
    private val handler = ApiCallHandler(scope, tag)
    private val keptResults = KeepLast<List<ClientResponse>>(scope)

    val state: StateFlow<UiState<List<ClientResponse>>> = keptResults.state
    val freshest: StateFlow<List<ClientResponse>?> = keptResults.freshest

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lastFiredQuery = MutableStateFlow("")
    val lastFiredQuery: StateFlow<String> = _lastFiredQuery.asStateFlow()

    private var searchJob: Job? = null
    private var latestQuery: String = ""
    private var generation = 0L
    private var debouncePending = false

    fun onQueryChange(query: String) {
        _query.value = query
        latestQuery = query
        invalidateSearch()
        val trimmed = query.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) {
            keptResults.stateFlow.value = UiState.Idle
            return
        }
        scheduleSearch(trimmed, debounce = true, entryMessage = "search called: query=$trimmed")
    }

    fun retrySearch() {
        val trimmed = latestQuery.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) return
        invalidateSearch()
        scheduleSearch(trimmed, debounce = false, entryMessage = "search retried: query=$trimmed")
    }

    /** Add newly created client to loaded results without coupling search instances. */
    fun include(client: ClientResponse) {
        if (latestQuery.trim().length < MIN_SEARCH_CHARS || keptResults.freshestValue() == null) return
        val reissuePendingDebounce = debouncePending
        invalidateSearch()
        keptResults.mutate { clients ->
            listOf(client) + clients.filterNot { it.id == client.id }
        }
        if (reissuePendingDebounce) {
            val trimmed = latestQuery.trim()
            scheduleSearch(trimmed, debounce = true, entryMessage = "search called: query=$trimmed")
        }
    }

    fun replace(client: ClientResponse) {
        invalidateSearch()
        if (latestQuery.trim().length >= MIN_SEARCH_CHARS) {
            keptResults.mutate { clients ->
                if (clients.none { it.id == client.id }) {
                    null
                } else {
                    clients.map { if (it.id == client.id) client else it }
                }
            }
        }
        refreshSearchAfterMutation()
    }

    fun remove(clientId: String) {
        invalidateSearch()
        if (latestQuery.trim().length >= MIN_SEARCH_CHARS) {
            keptResults.mutateRemoved { it.id == clientId }
        }
        refreshSearchAfterMutation()
    }

    private fun invalidateSearch() {
        generation++
        searchJob?.cancel()
        searchJob = null
        debouncePending = false
    }

    private fun scheduleSearch(
        query: String,
        debounce: Boolean,
        entryMessage: String,
    ) {
        val searchGeneration = generation
        debouncePending = debounce
        searchJob =
            scope.launch {
                if (debounce) delay(SEARCH_DEBOUNCE_MS)
                debouncePending = false
                launchSearch(query, searchGeneration, this, entryMessage)
            }
    }

    private fun refreshSearchAfterMutation() {
        val trimmed = latestQuery.trim()
        if (trimmed.length < MIN_SEARCH_CHARS) {
            keptResults.stateFlow.value = UiState.Idle
            return
        }
        scheduleSearch(
            trimmed,
            debounce = false,
            entryMessage = "search refreshed after client mutation: query=$trimmed",
        )
    }

    private fun launchSearch(
        query: String,
        searchGeneration: Long,
        requestScope: CoroutineScope,
        entryMessage: String,
    ) {
        _lastFiredQuery.value = query
        keptResults.stateFlow.value = UiState.Loading
        handler.launchStatelessGuarded(
            operation = "search",
            endpoint = "GET /api/clients",
            block = {
                apiClient.httpClient.get(ApiRoutes.CLIENTS) { parameter("q", query) }
            },
            guarded =
                GuardedStateless(
                    // #528 — the handler owns the boundary now: decode suspends, commit is inert
                    // when a newer keystroke/mutation bumped the generation mid-decode. The
                    // hand-rolled in-transform `if (searchGeneration == generation)` is deleted
                    // with it — retaining both would promise a guarantee in two places.
                    decode = { response -> response.body<List<ClientResponse>>() },
                    commit = { clients ->
                        keptResults.stateFlow.value = UiState.Success(clients)
                    },
                    scope = requestScope,
                    entryMessage = entryMessage,
                    onNonSuccess = { response ->
                        keptResults.stateFlow.value = UiState.Error("search failed: ${response.status.value}")
                    },
                    onError = { error ->
                        keptResults.stateFlow.value = UiState.Error(error.message ?: "Unknown error")
                    },
                    stale = { searchGeneration != generation },
                ),
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_SEARCH_CHARS = 2
    }
}
