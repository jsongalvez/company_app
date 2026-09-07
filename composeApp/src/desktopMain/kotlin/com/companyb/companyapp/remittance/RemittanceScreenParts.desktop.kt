package com.companyb.companyapp.remittance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.Spacing

// #120 D1 (desktop) — dense table: type / method / date range / status / net (submitted SESSION
// only). Remittance counts are small per branch (one SESSION + one PRODUCT draft per day), so a
// plain scrolling Column is fine (#113/#123 precedent).
@Composable
actual fun RemittanceRowList(
    remittances: List<RemittanceResponse>,
    onRemittanceClick: (RemittanceResponse) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            ) {
                TableCell("Type", modifier = Modifier.weight(1f), header = true)
                TableCell("Method", modifier = Modifier.weight(1f), header = true)
                TableCell("Date range", modifier = Modifier.weight(2f), header = true)
                TableCell("Status", modifier = Modifier.weight(1f), header = true)
                TableCell("Net", modifier = Modifier.weight(1f), header = true)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            remittances.forEach { remittance ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRemittanceClick(remittance) }
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                ) {
                    TableCell(remittanceTypeLabel(remittance.type.name), modifier = Modifier.weight(1f))
                    TableCell(remittanceMethodLabel(remittance.method.name), modifier = Modifier.weight(1f))
                    TableCell(
                        "${remittance.dateRangeStart} – ${remittance.dateRangeEnd}",
                        modifier = Modifier.weight(2f),
                    )
                    TableCell(remittance.status.name, modifier = Modifier.weight(1f))
                    val net = submittedSessionNet(remittance)
                    TableCell(
                        text = if (net != null) peso(net) else "—",
                        modifier = Modifier.weight(1f),
                        muted = net == null,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    header: Boolean = false,
    muted: Boolean = false,
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
            when {
                header || muted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        modifier = modifier,
    )
}
