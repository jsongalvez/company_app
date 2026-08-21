package com.companyb.companyapp.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * #113 — cross-screen client flow state, mirroring the [NotificationState] singleton pattern
 * (#109/#112): one-shot notices that the Clients search screen consumes on display.
 *
 * D1 — post-anonymize the detail screen pops back to the search list; the search entry's VM
 * stays alive under the push (entry-scoped, #112 pattern), but the detail entry owns a
 * *different* VM instance — so the "Client anonymized" confirmation crosses the VM boundary
 * through this singleton. The search screen collects the notice, shows the snackbar, and
 * consumes it (consumption prevents the stale notice re-snackbar-ing on a later visit).
 */
object ClientState {
    private val _anonymizeNotice = MutableStateFlow<String?>(null)
    val anonymizeNotice: StateFlow<String?> = _anonymizeNotice.asStateFlow()

    fun setAnonymizeNotice(message: String) {
        _anonymizeNotice.value = message
    }

    fun consumeAnonymizeNotice() {
        _anonymizeNotice.value = null
    }

    fun clear() {
        _anonymizeNotice.value = null
    }
}
