package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.ui.theme.Spacing

// #113 D3 (desktop) — dense table: name/phone/gender/age. Search results are the scan persona
// (the #95 re-scope justification); 20 rows max (backend limit), so a plain Column is fine.
@Composable
actual fun ClientResultList(
    results: List<ClientResponse>,
    onClientClick: (ClientResponse) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            TableCell("Name", modifier = Modifier.weight(2f), header = true)
            TableCell("Phone", modifier = Modifier.weight(2f), header = true)
            TableCell("Gender", modifier = Modifier.weight(1f), header = true)
            TableCell("Age", modifier = Modifier.weight(1f), header = true)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        results.forEach { client ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClientClick(client) }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                TableCell(clientDisplayName(client), modifier = Modifier.weight(2f))
                TableCell(client.phoneNumber.orEmpty(), modifier = Modifier.weight(2f))
                TableCell(client.gender.displayName(), modifier = Modifier.weight(1f))
                TableCell(client.age.toString(), modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    header: Boolean = false,
) {
    Text(
        text = text,
        style =
            if (header) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
        color =
            if (header) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        modifier = modifier,
    )
}

// #113 D4/D5 (desktop) — two-column (Identity | Contact+Health) + right-hand actions column
// (anonymize button per D5). The shared field rows come from commonMain; only the arrangement
// diverges (smallest-divergent-subtree per #95).
@Composable
actual fun ClientDetailLayout(
    identity: @Composable ColumnScope.() -> Unit,
    contactHealth: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
            identity()
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        Column(modifier = Modifier.weight(1f)) {
            contactHealth()
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(140.dp).padding(start = Spacing.md),
        ) {
            actions()
        }
    }
}
