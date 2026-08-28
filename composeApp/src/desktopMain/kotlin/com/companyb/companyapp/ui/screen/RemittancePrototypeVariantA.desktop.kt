package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun RemittancePrototypeVariantA(context: RemittancePrototypeContext) {
    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        GuidedStageRail(context.state)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            RemittanceIntro(
                eyebrow = "A / GUIDED REVIEW",
                title = "Submit with no hidden step",
                description =
                    "A linear checklist keeps the covered days, income lines, snapshot, and handoff in one order.",
            )
            GuidedRangeStage(context)
            GuidedLinesStage(context)
            RemittanceSectionPanel {
                RemittanceSnapshotReview(draft = context.selectedDraft)
            }
            RemittanceSectionPanel {
                RemittanceMethodNote(state = context.state)
            }
            RemittanceConfirmPanel(context)
            GuidedHistory(context)
        }
    }
}

@Composable
private fun GuidedStageRail(state: RemittancePrototypeState) {
    val steps = listOf("Pick range", "Review lines", "Snapshot", "Confirm")
    Surface(
        modifier = Modifier.fillMaxHeight().width(184.dp),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "New remittance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Four checks before money moves",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            steps.forEachIndexed { index, step ->
                GuidedStageStep(state = state, index = index, label = step)
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Drafts stay editable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Only confirmation is simulated.", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
    }
}

@Composable
private fun GuidedStageStep(
    state: RemittancePrototypeState,
    index: Int,
    label: String,
) {
    val selected = state.activeStep == index
    Surface(
        onClick = { state.activeStep = index },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        border =
            if (selected) {
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                )
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "0${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else InkSubtle,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

@Composable
private fun GuidedRangeStage(context: RemittancePrototypeContext) {
    RemittanceSectionPanel {
        Text(
            "01  ·  Covered days",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Choose draft, then choose the range this submission covers.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            context.drafts.forEach { draft ->
                RemittanceDraftChoice(
                    draft = remittanceDraftForRange(draft, context.selectedRange),
                    selected = draft.id == context.selectedDraft.id,
                    onClick = { context.selectDraft(draft) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        RemittanceRangeChooser(
            selectedRange = context.selectedRange,
            onRangeSelect = context.selectRange,
        )
    }
}

@Composable
private fun GuidedLinesStage(context: RemittancePrototypeContext) {
    RemittanceSectionPanel {
        RemittanceLineReview(draft = context.selectedDraft)
    }
}

@Composable
private fun GuidedHistory(context: RemittancePrototypeContext) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(bottom = Spacing.md)) {
        Text(
            "Submitted history",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text("Same branch, already locked", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        context.history.forEach { history ->
            RemittanceHistoryRow(history)
        }
    }
}
