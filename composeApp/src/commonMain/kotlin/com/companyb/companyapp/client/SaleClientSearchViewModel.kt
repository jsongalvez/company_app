package com.companyb.companyapp.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.network.ApiClient
import kotlinx.coroutines.flow.StateFlow

/**
 * #610 — the entry-scoped client owner for commerce buyer selection: search state and intents
 * only, behind [ClientSearchApi]. Reuses the existing [ClientSearcher] (debounce, cancellation
 * and mutation reconciliation); exposes no detail load, create/update/anonymize, or conflict
 * surface, so commerce cannot accidentally invoke client editing/privacy operations.
 * Each inventory entry constructs its own instance, so mutable query state never leaks across
 * entries.
 */
class SaleClientSearchViewModel(
    apiClient: ApiClient,
) : ViewModel(),
    ClientSearchApi {
    private val clientSearcher = ClientSearcher(apiClient, viewModelScope, "SaleClientPickerVM")

    override val query: StateFlow<String> = clientSearcher.query
    override val searchResults: StateFlow<UiState<List<ClientResponse>>> = clientSearcher.state
    override val freshestResults: StateFlow<List<ClientResponse>?> = clientSearcher.freshest
    override val onQueryChange: (String) -> Unit = clientSearcher::onQueryChange
    override val retrySearch: () -> Unit = clientSearcher::retrySearch

    override fun applyClientMutation(mutation: ClientMutation) {
        if (!mutation.refreshSearch) return
        if (mutation.client == null) {
            clientSearcher.remove(mutation.clientId)
        } else {
            clientSearcher.replace(mutation.client)
        }
    }
}
