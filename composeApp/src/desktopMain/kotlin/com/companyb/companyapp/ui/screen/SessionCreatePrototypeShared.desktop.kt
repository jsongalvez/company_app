package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun PrototypeHeader(
    branchName: String?,
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
                text = "Start a session",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = branchName ?: "Demo branch",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
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
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
internal fun BoxScope.PrototypeSwitcher(
    currentVariant: SessionCreatePrototypeVariant,
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
                onClick = { onVariantChange(previousSessionCreatePrototypeVariant(currentVariant.ordinal)) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text("<")
            }
            SessionCreatePrototypeVariant.values().forEach { variant ->
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
                        text = "${variant.key} ${variant.title}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
            TextButton(
                onClick = { onVariantChange(nextSessionCreatePrototypeVariant(currentVariant.ordinal)) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text(">")
            }
        }
    }
}

@Composable
internal fun PrototypeClientRow(
    client: FakeSessionClient,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
        border =
            if (selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PrototypeAvatar(client.initials)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${client.phone}  ·  ${client.lastVisit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            Text(
                text = client.sessionCount,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else InkSubtle,
            )
        }
    }
}

@Composable
internal fun PrototypeAvatar(initials: String) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun PrototypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.pill),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        border =
            if (selected) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
internal fun PrototypeSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = InkSubtle,
    )
}

internal fun Modifier.trackPrototypeTextFocus(state: SessionCreatePrototypeState): Modifier =
    onFocusChanged { state.textFieldFocused = it.isFocused }

@Composable
internal fun PrototypeEmptySearch(onReset: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = "No client matches this search",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Create a new client or clear search to use demo records.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                Text("Clear search")
            }
        }
    }
}

@Composable
internal fun PrototypeSubmissionNotice(submitted: Boolean) {
    if (submitted) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Text(
                text = "Demo draft ready. No session was sent to the backend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(Spacing.sm),
            )
        }
    }
}
