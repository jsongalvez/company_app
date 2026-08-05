package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.clickable
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
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

// #113 D3 (mobile) — card list: name primary, phone secondary. Card tap → push detail.
@Composable
actual fun ClientResultList(
    results: List<ClientResponse>,
    onClientClick: (ClientResponse) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(results) { client ->
            Surface(
                onClick = { onClientClick(client) },
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = clientDisplayName(client),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val phone = client.phoneNumber
                    if (!phone.isNullOrBlank()) {
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// #113 D4/D5 (mobile) — single column; the anonymize action sits at the bottom action row.
@Composable
actual fun ClientDetailLayout(
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
            // actions is a ColumnScope lambda (shared across actuals) — wrap in a Column.
            Column {
                actions()
            }
        }
    }
}
