package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun DashboardPrototypeHeader(
    data: FakeDashboardData,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(
                text = "Branch dashboard",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${data.branch}  /  ${data.date}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
                NotificationBadge(data.unreadNotifications)
            }
            PrototypeMarker()
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun PrototypeMarker() {
    Surface(
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Text(
            text = "DESKTOP PROTOTYPE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

@Composable
internal fun DashboardDatasetSwitcher(
    currentDataset: DashboardPrototypeDataset,
    onDatasetChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("Demo data", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        DashboardPrototypeDataset.values().forEach { dataset ->
            val selected = dataset == currentDataset
            Surface(
                onClick = { onDatasetChange(dataset.ordinal) },
                shape = RoundedCornerShape(CornerRadius.pill),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = "${dataset.key}  ${dataset.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
        Text(
            text = "Switch density to test information hierarchy",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
internal fun BoxScope.DashboardPrototypeSwitcher(
    currentVariant: DashboardPrototypeVariant,
    onVariantChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md),
        shape = RoundedCornerShape(CornerRadius.pill),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Layout",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
            TextButton(
                onClick = { onVariantChange(previousDashboardPrototypeVariant(currentVariant.ordinal)) },
            ) {
                Text("<", color = MaterialTheme.colorScheme.onPrimary)
            }
            DashboardPrototypeVariant.values().forEach { variant ->
                val selected = variant == currentVariant
                Surface(
                    onClick = { onVariantChange(variant.ordinal) },
                    shape = RoundedCornerShape(CornerRadius.sm),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(
                                alpha = 0.18f,
                            )
                        } else {
                            Color.Transparent
                        },
                ) {
                    Text(
                        text = "${variant.key}  ${variant.title}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
            TextButton(
                onClick = { onVariantChange(nextDashboardPrototypeVariant(currentVariant.ordinal)) },
            ) {
                Text(">", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
