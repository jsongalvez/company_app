package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun RemittancePrototypeVariantC(context: RemittancePrototypeContext) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DraftNavigatorQueue(context, Modifier.width(260.dp).fillMaxHeight())
        DraftNavigatorWorkspace(context, Modifier.weight(1f).fillMaxHeight())
        DraftNavigatorPreview(context, Modifier.width(260.dp).fillMaxHeight())
    }
}

@Composable
private fun DraftNavigatorQueue(
    context: RemittancePrototypeContext,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "Remittance queue",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("Start from an open draft", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            context.drafts.forEach { draft ->
                RemittanceDraftChoice(
                    draft = remittanceDraftForRange(draft, context.selectedRange),
                    selected = draft.id == context.selectedDraft.id,
                    onClick = { context.selectDraft(draft) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Locked history",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            context.history.forEach { history ->
                RemittanceHistoryRow(history)
            }
        }
    }
}

@Composable
private fun DraftNavigatorWorkspace(
    context: RemittancePrototypeContext,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RemittanceIntro(
            eyebrow = "C / DRAFT NAVIGATOR",
            title = "Open a draft, inspect its trail",
            description = "A list-first flow makes prior submissions easy to compare before drilling into one handoff.",
        )
        NavigatorDraftHeader(context.selectedDraft, context.selectedRange)
        NavigatorSteps(context)
        when (context.state.activeStep) {
            0 -> NavigatorRangeStage(context)
            1 -> NavigatorLinesStage(context)
            2 -> NavigatorSnapshotStage(context)
            else -> NavigatorMethodStage(context)
        }
        Text(
            "Use the stage tabs to walk the same draft in a different order.",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
    }
}

@Composable
private fun NavigatorDraftHeader(
    draft: FakeRemittanceDraft,
    selectedRange: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.md)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    draft.type,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Range $selectedRange  ·  ${draft.days}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            Text("DRAFT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NavigatorSteps(context: RemittancePrototypeContext) {
    val steps = listOf("Range", "Lines", "Snapshot", "Method + confirm")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        steps.forEachIndexed { index, step ->
            val selected = context.state.activeStep == index
            Surface(
                onClick = { context.state.activeStep = index },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(CornerRadius.sm),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = "${index + 1}. $step",
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

@Composable
private fun NavigatorRangeStage(context: RemittancePrototypeContext) {
    RemittanceSectionPanel {
        RemittanceRangeChooser(
            selectedRange = context.selectedRange,
            onRangeSelect = context.selectRange,
        )
        Spacer(Modifier.padding(Spacing.xxs))
        Text(
            "Next: inspect source lines, then verify the immutable handoff.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
    }
}

@Composable
private fun NavigatorLinesStage(context: RemittancePrototypeContext) {
    RemittanceSectionPanel {
        RemittanceLineReview(draft = context.selectedDraft)
    }
}

@Composable
private fun NavigatorSnapshotStage(context: RemittancePrototypeContext) {
    RemittanceSectionPanel {
        RemittanceSnapshotReview(draft = context.selectedDraft)
    }
}

@Composable
private fun NavigatorMethodStage(context: RemittancePrototypeContext) {
    RemittanceSectionPanel {
        RemittanceMethodNote(state = context.state)
        RemittanceConfirmPanel(context)
    }
}

@Composable
private fun DraftNavigatorPreview(
    context: RemittancePrototypeContext,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "Handoff preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Always visible while you inspect the draft",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            RemittanceSnapshotReview(draft = context.selectedDraft, compact = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Method", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
            Text(
                context.state.method.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(context.state.note, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            Spacer(Modifier.padding(Spacing.xxs))
            if (context.state.submitted) {
                Text(
                    "Demo confirmed. Real submission would lock covered days here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { context.state.submitted = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset demo")
                }
            } else {
                Button(onClick = context.confirmSubmission, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm demo")
                }
            }
        }
    }
}
