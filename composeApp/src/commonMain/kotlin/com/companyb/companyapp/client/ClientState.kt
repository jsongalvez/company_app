package com.companyb.companyapp.client

import com.companyb.companyapp.dto.ClientResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClientMutation(
    val clientId: String,
    val client: ClientResponse?,
    val refreshSearch: Boolean = true,
)

class ClientMutationLease internal constructor(
    internal val token: Any,
)

class ClientSnapshotLease internal constructor(
    internal val clientId: String,
    internal val requestId: Long,
    internal val mutationGeneration: Long,
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
    private val clientMutationOwner = MutableStateFlow<Any?>(null)
    private var mutationGeneration = 0L
    private var snapshotRequestSequence = 0L
    private val latestSnapshotRequestByClient = mutableMapOf<String, Long>()

    fun setAnonymizeNotice(message: String) {
        _anonymizeNotice.value = message
    }

    internal fun beginClientSnapshot(clientId: String): ClientSnapshotLease {
        snapshotRequestSequence++
        latestSnapshotRequestByClient[clientId] = snapshotRequestSequence
        return ClientSnapshotLease(clientId, snapshotRequestSequence, mutationGeneration)
    }

    internal fun publishClientSnapshot(
        lease: ClientSnapshotLease,
        client: ClientResponse?,
    ): Boolean {
        if (clientMutationOwner.value != null ||
            mutationGeneration != lease.mutationGeneration ||
            latestSnapshotRequestByClient[lease.clientId] != lease.requestId
        ) {
            return false
        }
        _clientMutation.value = ClientMutation(lease.clientId, client, refreshSearch = false)
        return true
    }

    internal fun publishClientMutation(
        lease: ClientMutationLease,
        clientId: String,
        client: ClientResponse?,
    ): Boolean {
        if (clientMutationOwner.value !== lease.token) return false
        mutationGeneration++
        _clientMutation.value = ClientMutation(clientId, client)
        return true
    }

    internal fun tryStartClientMutation(): ClientMutationLease? {
        val token = Any()
        if (!clientMutationOwner.compareAndSet(null, token)) return null
        mutationGeneration++
        _clientMutationInFlight.value = true
        return ClientMutationLease(token)
    }

    internal fun finishClientMutation(lease: ClientMutationLease) {
        if (clientMutationOwner.compareAndSet(lease.token, null)) {
            mutationGeneration++
            _clientMutationInFlight.value = false
        }
    }

    fun consumeAnonymizeNotice() {
        _anonymizeNotice.value = null
    }

    fun clear() {
        mutationGeneration++
        latestSnapshotRequestByClient.clear()
        _anonymizeNotice.value = null
        _clientMutation.value = null
        clientMutationOwner.value = null
        _clientMutationInFlight.value = false
    }
}
