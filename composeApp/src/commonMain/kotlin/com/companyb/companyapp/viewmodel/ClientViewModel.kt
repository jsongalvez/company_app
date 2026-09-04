package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.ClientMutation
import com.companyb.companyapp.state.ClientMutationLease
import com.companyb.companyapp.state.ClientState
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClientViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ClientVM")
    private val clientSearcher = ClientSearcher(apiClient, viewModelScope, "ClientVM")

    val searchResults: StateFlow<UiState<List<ClientResponse>>> = clientSearcher.state
    val freshestResults: StateFlow<List<ClientResponse>?> = clientSearcher.freshest
    val query: StateFlow<String> = clientSearcher.query
    val lastFiredQuery: StateFlow<String> = clientSearcher.lastFiredQuery

    private val _clientDetail = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val clientDetail: StateFlow<UiState<ClientResponse>> = _clientDetail.asStateFlow()

    private val _updateClientState = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val updateClientState: StateFlow<UiState<ClientResponse>> = _updateClientState.asStateFlow()

    private val _anonymizeState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val anonymizeState: StateFlow<UiState<Unit>> = _anonymizeState.asStateFlow()

    // #348 — create-client dialog state (the createUserResult shape).
    private val _createClientResult = MutableStateFlow<UiState<ClientResponse>>(UiState.Idle)
    val createClientResult: StateFlow<UiState<ClientResponse>> = _createClientResult.asStateFlow()

    // D4 — 409 "changed elsewhere" indication: the PATCH conflicted, the detail was reloaded,
    // and the screen shows a one-shot notice so the user knows their edit didn't win.
    private val _detailChangedNotice = MutableStateFlow(false)
    val detailChangedNotice: StateFlow<Boolean> = _detailChangedNotice.asStateFlow()

    private var detailJob: Job? = null
    private var lastAppliedMutation: ClientMutation? = null

    fun onQueryChange(query: String) {
        clientSearcher.onQueryChange(query)
    }

    fun retrySearch() = clientSearcher.retrySearch()

    fun loadClient(
        clientId: String,
        resetNotice: Boolean = true,
        mutationLease: ClientMutationLease? = null,
        publishMutation: Boolean = false,
    ) {
        // Reset the changed-elsewhere notice on entry loads; the 409 conflict-reload passes
        // resetNotice = false so the notice it just raised survives the re-fetch.
        if (resetNotice) {
            _detailChangedNotice.value = false
        }
        val snapshotLease = if (publishMutation) ClientState.beginClientSnapshot(clientId) else null
        // Tracked so a PATCH commit can cancel an in-flight reload (see updateClient): the detail
        // flow has two writers, and a stale GET landing after a fresher PATCH commit would
        // revert the display to pre-edit data.
        detailJob?.cancel()
        _clientDetail.value = UiState.Loading
        detailJob =
            handler
                .launch(
                    LaunchRequest(
                        state = _clientDetail,
                        operation = "loadClient",
                        endpoint = "GET /api/clients/$clientId",
                        entryMessage = "loadClient called: clientId=$clientId",
                        block = { apiClient.httpClient.get(ApiRoutes.client(clientId)) },
                        transform = {
                            val client = it.body<ClientResponse>()
                            if (publishMutation) {
                                val snapshot = client.takeIf { it.firstName != null && it.lastName != null }
                                if (mutationLease != null) {
                                    ClientState.publishClientMutation(mutationLease, clientId, snapshot)
                                } else {
                                    snapshotLease?.let { ClientState.publishClientSnapshot(it, snapshot) }
                                }
                            }
                            mutationLease?.let(ClientState::finishClientMutation)
                            client
                        },
                        onNonSuccess = {
                            mutationLease?.let(ClientState::finishClientMutation)
                            false
                        },
                        onError = {
                            mutationLease?.let(ClientState::finishClientMutation)
                        },
                    ),
                ).also { job ->
                    job.invokeOnCompletion { cause ->
                        if (cause is CancellationException) mutationLease?.let(ClientState::finishClientMutation)
                    }
                }
    }

    fun applyClientMutation(mutation: ClientMutation) {
        if (lastAppliedMutation == mutation) return
        if (!mutation.refreshSearch) {
            lastAppliedMutation = mutation
            return
        }
        if (mutation.client == null) {
            clientSearcher.remove(mutation.clientId)
        } else {
            clientSearcher.replace(mutation.client)
        }
        lastAppliedMutation = mutation
    }

    fun updateClient(
        clientId: String,
        request: UpdateClientRequest,
    ) {
        if (_updateClientState.value is UiState.Loading) return
        val mutationLease = ClientState.tryStartClientMutation() ?: return
        _updateClientState.value = UiState.Loading
        handler
            .launch(
                LaunchRequest(
                    state = _updateClientState,
                    operation = "updateClient",
                    endpoint = "PATCH /api/clients/$clientId",
                    entryMessage = "updateClient called: clientId=$clientId",
                    block = {
                        apiClient.httpClient.patch(ApiRoutes.client(clientId)) {
                            setBody(request)
                        }
                    },
                    // D4 — the PATCH response IS the updated record: commit it straight into the detail
                    // flow so the display shows the new value when edit mode exits (the screen renders
                    // clientDetail, not updateClientState — without this the edit would look lost). The
                    // reload-cancel keeps the commit from being overwritten by a stale in-flight GET.
                    // A success also retires any changed-elsewhere banner: the user's edit just won.
                    transform = { response ->
                        detailJob?.cancel()
                        _detailChangedNotice.value = false
                        val updated = response.body<ClientResponse>()
                        ClientState.publishClientMutation(mutationLease, clientId, updated)
                        ClientState.finishClientMutation(mutationLease)
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
                                ClientState.finishClientMutation(mutationLease)
                                _updateClientState.value = UiState.Idle
                                true
                            }

                            HttpStatusCode.Conflict, HttpStatusCode.NotFound -> {
                                _updateClientState.value = UiState.Idle
                                if (response.status == HttpStatusCode.Conflict) {
                                    _detailChangedNotice.value = true
                                }
                                loadClient(
                                    clientId,
                                    resetNotice = false,
                                    mutationLease = mutationLease,
                                    publishMutation = true,
                                )
                                true
                            }

                            else -> {
                                ClientState.finishClientMutation(mutationLease)
                                false
                            }
                        }
                    },
                    onError = { ClientState.finishClientMutation(mutationLease) },
                ),
            ).also { job ->
                job.invokeOnCompletion { cause ->
                    if (cause is CancellationException) ClientState.finishClientMutation(mutationLease)
                }
            }
    }

    fun anonymizeClient(clientId: String) {
        if (_anonymizeState.value is UiState.Loading) return
        val mutationLease = ClientState.tryStartClientMutation() ?: return
        _anonymizeState.value = UiState.Loading
        handler
            .launch(
                LaunchRequest(
                    state = _anonymizeState,
                    operation = "anonymizeClient",
                    endpoint = "POST /api/clients/$clientId/anonymize",
                    entryMessage = "anonymizeClient called: clientId=$clientId",
                    block = { apiClient.httpClient.post(ApiRoutes.clientAnonymize(clientId)) },
                    // 204 no body — transform runs only on success; the notice crosses the VM boundary to
                    // the search screen's snackbar via ClientState (D1; the detail entry's VM is a
                    // different instance than the search entry's — see ClientState doc comment).
                    transform = {
                        if (ClientState.publishClientMutation(mutationLease, clientId, null)) {
                            ClientState.setAnonymizeNotice("Client anonymized")
                        }
                        ClientState.finishClientMutation(mutationLease)
                        Unit
                    },
                    onNonSuccess = {
                        ClientState.finishClientMutation(mutationLease)
                        false
                    },
                    onError = { ClientState.finishClientMutation(mutationLease) },
                ),
            ).also { job ->
                job.invokeOnCompletion { cause ->
                    if (cause is CancellationException) ClientState.finishClientMutation(mutationLease)
                }
            }
    }

    // #348 — client-create dialog result: Success closes the dialog (the create-user shape);
    // Error carries the backend's 400 policy message inline via extractApiErrorMessage. The
    // created row prepends to the keep-last search cache so it is visible without a re-search;
    // a cache that never loaded simply skips the prepend. Double-submit is safe twice over:
    // this Loading guard plus the client-generated UUID idempotency key (BR §390–392).
    fun createClient(request: CreateClientRequest) {
        if (_createClientResult.value is UiState.Loading) return
        _createClientResult.value = UiState.Loading
        handler.launch(
            LaunchRequest(
                state = _createClientResult,
                operation = "createClient",
                endpoint = "POST /api/clients",
                block = { apiClient.httpClient.post(ApiRoutes.CLIENTS) { setBody(request) } },
                transform = { response ->
                    val created = response.body<ClientResponse>()
                    clientSearcher.include(created)
                    created
                },
                onNonSuccess = { response ->
                    val detail =
                        extractApiErrorMessage(runCatching { response.bodyAsText() }.getOrNull())
                    _createClientResult.value =
                        UiState.Error(detail ?: "Create client failed: ${response.status.value}")
                    true
                },
            ),
        )
    }

    fun consumeCreateClientResult() {
        if (_createClientResult.value is UiState.Success) {
            _createClientResult.value = UiState.Idle
        }
    }
}
