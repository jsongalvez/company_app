package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse

// #113 D3 — smallest-divergent-subtree per #95's opt-in re-scope (search results are the scan
// persona): shared chrome lives in ClientsScreen (commonMain), only the result list rendering
// diverges — desktop dense table / mobile card list. VMs stay in commonMain (one test target).
@Composable
expect fun ClientResultList(
    results: List<ClientResponse>,
    onClientClick: (ClientResponse) -> Unit,
)

// #113 D4/D5 — detail layout split: desktop two-column (Identity | Contact+Health) + right-hand
// actions column (anonymize button); mobile single column with the action at the bottom. Field
// rows + the edit state machine stay commonMain; only the column arrangement diverges.
@Composable
expect fun ClientDetailLayout(
    identity: @Composable ColumnScope.() -> Unit,
    contactHealth: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
)

// Shared display helpers — used by both platform actuals + the commonMain screens.
// D10 — a null firstName/lastName pair is the only in-band anonymized signal (F3): blank display.
internal fun clientDisplayName(client: ClientResponse): String {
    if (client.firstName == null || client.lastName == null) return ""
    return buildString {
        append(client.firstName)
        client.middleName?.takeIf { it.isNotBlank() }?.let {
            append(' ')
            append(it)
        }
        append(' ')
        append(client.lastName)
        client.suffix?.takeIf { it.isNotBlank() }?.let {
            append(", ")
            append(it)
        }
    }
}

internal fun Gender.displayName(): String = if (this == Gender.M) "Male" else "Female"
