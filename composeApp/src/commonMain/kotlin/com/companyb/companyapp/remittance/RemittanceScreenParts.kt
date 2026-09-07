package com.companyb.companyapp.remittance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover

// #120 D1 — smallest-divergent-subtree per #95/#99 (AuditLogScreen D11 precedent): shared chrome
// (tabs, header, create popup, state branches) lives in RemittanceListScreen (commonMain); only
// the row list rendering diverges — desktop dense table / mobile card list. VMs stay commonMain.
@Composable
fun MobileRemittanceRowList(
    remittances: List<RemittanceResponse>,
    onRemittanceClick: (RemittanceResponse) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(remittances) { remittance ->
            Surface(
                shape = RoundedCornerShape(CornerRadius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .clickable { onRemittanceClick(remittance) }
                            .rowHover(shape = RoundedCornerShape(CornerRadius.md))
                            .padding(Spacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Text(
                            text = remittanceTypeLabel(remittance.type.name),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.Text(
                            text = remittance.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Text(
                        text = remittanceMethodLabel(remittance.method.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    androidx.compose.material3.Text(
                        text = "${remittance.dateRangeStart} – ${remittance.dateRangeEnd}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    submittedSessionNet(remittance)?.let { net ->
                        androidx.compose.material3.Text("Net ${peso(net)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
expect fun RemittanceRowList(
    remittances: List<RemittanceResponse>,
    onRemittanceClick: (RemittanceResponse) -> Unit,
)
