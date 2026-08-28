package com.companyb.companyapp.state

import com.companyb.companyapp.dto.ClientResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClientMutation(
    val clientId: String,
    val client: ClientResponse?,
)

/**
 * #113 — cross-screen client flow state, mirroring the [NotificationState] singleton pattern
 * (#109/#112): one-shot notices that the Clients search screen consumes on display.
 *
 * D1 — post-anonymize the detail screen pops back to the search list; the search entry's VM
 * stays alive under the push (entry-scoped, #112 pattern), but the detail entry owns a
 * *different* VM instance — so the "Client anonymized" confirmation crosses the VM boundary
 * through this singleton. The search screen collects the notice, shows the snackbar, and
 * consumes it (consumption prevents the stale notice re-snackbar-ing on a later visit).
 * Client mutations retain the latest authoritative client snapshot so entry-scoped session and
 * client search VMs can reconcile after profile navigation without losing draft state.
 */
object ClientState {
    private val _anonymizeNotice = MutableStateFlow<String?>(null)
    val anonymizeNotice: StateFlow<String?> = _anonymizeNotice.asStateFlow()
    private val _clientMutation = MutableStateFlow<ClientMutation?>(null)
    val clientMutation: StateFlow<ClientMutation?> = _clientMutation.asStateFlow()
    private val _clientMutationInFlight = MutableStateFlow(false)
    val clientMutationInFlight: StateFlow<Boolean> = _clientMutationInFlight.asStateFlow()

    fun setAnonymizeNotice(message: String) {
        _anonymizeNotice.value = message
    }

    fun setClientMutation(
        clientId: String,
        client: ClientResponse?,
    ) {
        _clientMutation.value = ClientMutation(clientId, client)
    }

    fun setClientMutationInFlight(value: Boolean) {
        _clientMutationInFlight.value = value
    }

    fun consumeAnonymizeNotice() {
        _anonymizeNotice.value = null
    }

    fun clear() {
        _anonymizeNotice.value = null
        _clientMutation.value = null
        _clientMutationInFlight.value = false
    }
}
