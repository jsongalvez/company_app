package com.companyb.companyapp.client

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import kotlinx.coroutines.flow.StateFlow

/**
 * #610 — the narrow client-owned search boundary: query + results + intents only, never the
 * client editing/privacy surface (detail load, create/update/anonymize, conflict notices).
 * Commerce buyer selection consumes this seam; its UI cannot accidentally invoke editing
 * operations. The existing [ClientSearcher] still owns debounce, cancellation and mutation
 * reconciliation behind every implementation.
 */
interface ClientSearchApi {
    val query: StateFlow<String>
    val searchResults: StateFlow<UiState<List<ClientResponse>>>

    /**
     * #673 — keep-last render payload: the freshest successful list, retained across
     * Loading/Error so stale rows stay visible during refresh (but not selectable).
     * Still search-only; never editing/privacy surface.
     */
    val freshestResults: StateFlow<List<ClientResponse>?>
    val onQueryChange: (String) -> Unit
    val retrySearch: () -> Unit

    fun applyClientMutation(mutation: ClientMutation)
}

/**
 * #558 — the narrow client-owned picker boundary: search + select only, never the
 * session form draft/preview/submit surface. Session creation consumes this seam
 * (its `SessionClientPickerApi` extends it); product-sale consumes [ClientSearchApi]
 * (selection stays sale-local, #610).
 */
interface ClientPickerApi : ClientSearchApi {
    fun selectClient(client: ClientResponse)
}
