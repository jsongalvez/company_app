package com.companyb.companyapp.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

// #113 D3 — smallest-divergent-subtree per #95's opt-in re-scope (search results are the scan
// persona): shared chrome lives in ClientsScreen (commonMain), only the result list rendering
// diverges — desktop dense table / mobile card list. VMs stay in commonMain (one test target).
@Composable
fun MobileClientResultList(
    results: List<ClientResponse>,
    onClientClick: (ClientResponse) -> Unit,
    enabled: Boolean = true,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(results) { client ->
            Surface(
                onClick = { onClientClick(client) },
                enabled = enabled,
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(text = clientPrimaryName(client), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = clientSecondaryLine(client, results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// #113 D4/D5 — detail layout split: desktop two-column (Identity+Contact | Health) +
// right-hand actions column (More menu per #673); mobile single column with the action
// at the bottom. Field rows + the edit state machine stay commonMain; only the column
// arrangement diverges.
@Composable
fun MobileClientDetailLayout(
    identity: @Composable ColumnScope.() -> Unit,
    contactHealth: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        identity()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        contactHealth()
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
        ) {
            Column { actions() }
        }
    }
}

@Composable
expect fun ClientResultList(
    results: List<ClientResponse>,
    onClientClick: (ClientResponse) -> Unit,
    enabled: Boolean = true,
)

@Composable
expect fun ClientDetailLayout(
    identity: @Composable ColumnScope.() -> Unit,
    contactHealth: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
)

@Composable
expect fun ClientDetailBackHandler(enabled: Boolean)

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
