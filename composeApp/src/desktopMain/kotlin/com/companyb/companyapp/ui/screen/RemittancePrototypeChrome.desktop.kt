package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun RemittancePrototypeHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("Exit") }
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(
                text = "Remittance workspace",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Makati Central  ·  Thursday, 27 Aug 2026  ·  fake data",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        RemittancePrototypeMarker()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun RemittancePrototypeMarker() {
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
internal fun RemittanceIntro(
    eyebrow: String,
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(description, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
    }
}

@Composable
internal fun BoxScope.RemittancePrototypeSwitcher(
    currentVariant: RemittancePrototypeVariant,
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
                onClick = { onVariantChange(previousRemittancePrototypeVariant(currentVariant.ordinal)) },
                modifier = Modifier.semantics { contentDescription = "Previous layout" },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text("<")
            }
            RemittancePrototypeVariant.values().forEach { variant ->
                val selected = variant == currentVariant
                Surface(
                    onClick = { onVariantChange(variant.ordinal) },
                    shape = RoundedCornerShape(CornerRadius.sm),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
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
                onClick = { onVariantChange(nextRemittancePrototypeVariant(currentVariant.ordinal)) },
                modifier = Modifier.semantics { contentDescription = "Next layout" },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text(">")
            }
        }
    }
}
