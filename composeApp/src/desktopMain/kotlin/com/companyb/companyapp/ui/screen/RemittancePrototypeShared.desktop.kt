package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun RemittanceDraftChoice(
    draft: FakeRemittanceDraft,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.md),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        border =
            BorderStroke(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    draft.type,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text("DRAFT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(draft.range, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            Text(
                text =
                    if (draft.type ==
                        "HEALot"
                    ) {
                        "${draft.lines.size} sessions · ${draft.net} net"
                    } else {
                        "${draft.lines.size} product lines · ${draft.total}"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else InkSubtle,
            )
        }
    }
}

@Composable
internal fun RemittanceRangeChooser(
    selectedRange: String,
    onRangeSelect: (Int) -> Unit,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "1  ·  Pick covered range",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Drafts are flexible; submission locks the selected days.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.xxs else Spacing.xs),
        ) {
            remittancePrototypeRanges.forEachIndexed { index, range ->
                val selected = range == selectedRange
                Surface(
                    onClick = { onRangeSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(CornerRadius.sm),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = range,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RemittanceLineReview(
    draft: FakeRemittanceDraft,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "2  ·  Review income lines",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text("${draft.lines.size} lines", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        }
        draft.lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(
                                if (compact) 6.dp else 8.dp,
                            ).background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.xs)) {
                    Text(
                        line.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${line.kind}  ·  ${line.detail}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSubtle,
                    )
                }
                Text(
                    line.amount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Line total",
                style = MaterialTheme.typography.labelSmall,
                color = InkSubtle,
                modifier = Modifier.weight(1f),
            )
            Text(draft.total, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun RemittanceSnapshotReview(
    draft: FakeRemittanceDraft,
    compact: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "3  ·  Snapshot review",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "LOCKS ON SUBMIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text =
                    if (draft.type ==
                        "HEALot"
                    ) {
                        "Financial snapshot freezes gross, compensation, expenses, and net."
                    } else {
                        "Product review uses line-derived amounts; commission is excluded " +
                            "and no SESSION snapshot is written."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            SnapshotValue("Gross", draft.gross)
            SnapshotValue("Compensation", draft.compensation)
            SnapshotValue("Expenses", draft.expenses)
            SnapshotValue("Net", draft.net)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SnapshotValue(
                if (draft.type == "HEALot") "To remit" else "Product total",
                if (draft.type == "HEALot") draft.net else draft.total,
                emphasized = true,
            )
        }
    }
}

@Composable
private fun SnapshotValue(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSubtle, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun RemittanceMethodNote(
    state: RemittancePrototypeState,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "4  ·  Method and note",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
            FakeRemittanceMethod.values().forEach { method ->
                val selected = method == state.method
                Surface(
                    onClick = { if (!state.submitted) state.method = method },
                    enabled = !state.submitted,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(CornerRadius.sm),
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = method.label,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier =
                            Modifier.padding(
                                horizontal = Spacing.xs,
                                vertical = if (compact) Spacing.xs else Spacing.sm,
                            ),
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.note,
            onValueChange = { state.note = it },
            enabled = !state.submitted,
            label = { Text("Remittance note") },
            singleLine = compact,
            minLines = if (compact) 1 else 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().trackRemittancePrototypeTextFocus(state),
        )
    }
}

@Composable
internal fun RemittanceConfirmPanel(
    context: RemittancePrototypeContext,
    compact: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(if (compact) Spacing.sm else Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    "Ready to confirm",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "${context.selectedDraft.type}  ·  ${context.selectedRange}  ·  " +
                            context.state.method.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
                Text(
                    text =
                        if (context.selectedDraft.type == "HEALot") {
                            "To remit  ·  ${context.selectedDraft.net}"
                        } else {
                            "Product total  ·  ${context.selectedDraft.total}"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (context.state.submitted) {
                    Text(
                        "Demo confirmed — no snapshot or API request was created.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (context.state.submitted) {
                OutlinedButton(onClick = { context.state.submitted = false }) { Text("Reset") }
            } else {
                Button(onClick = context.confirmSubmission) { Text("Confirm demo") }
            }
        }
    }
}

@Composable
internal fun RemittanceHistoryRow(history: FakeSubmittedRemittance) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${history.type}  ·  ${history.range}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${history.submittedAt}  ·  ${history.method.label}",
                style = MaterialTheme.typography.labelSmall,
                color = InkSubtle,
            )
        }
        Text(history.total, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun RemittanceSectionPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            content()
        }
    }
}

internal fun Modifier.trackRemittancePrototypeTextFocus(state: RemittancePrototypeState): Modifier =
    onFocusChanged { state.textFieldFocused = it.isFocused }
