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
internal fun RemittancePrototypeVariantB(context: RemittancePrototypeContext) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        RemittanceIntro(
            eyebrow = "B / CONTROL DESK",
            title = "See queue, edit, and money at once",
            description =
                "A one-page desk keeps draft selection on the left and a persistent submission brief on the right.",
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ControlDraftQueue(context, Modifier.width(236.dp).fillMaxHeight())
            ControlEditor(context, Modifier.weight(1f).fillMaxHeight())
            ControlSummary(context, Modifier.width(244.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun ControlDraftQueue(
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
                "Draft queue",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("Choose a flow to review", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            context.drafts.forEach { draft ->
                RemittanceDraftChoice(
                    draft = remittanceDraftForRange(draft, context.selectedRange),
                    selected = draft.id == context.selectedDraft.id,
                    onClick = { context.selectDraft(draft) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Submitted history",
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
private fun ControlEditor(
    context: RemittancePrototypeContext,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ControlProgressStrip()
        RemittanceSectionPanel {
            RemittanceRangeChooser(
                selectedRange = context.selectedRange,
                onRangeSelect = context.selectRange,
                compact = true,
            )
        }
        RemittanceSectionPanel {
            RemittanceLineReview(draft = context.selectedDraft, compact = true)
        }
        RemittanceSectionPanel {
            RemittanceMethodNote(state = context.state, compact = true)
        }
        RemittanceConfirmPanel(context)
        Text(
            "Demo only · submit creates no snapshot and touches no backend.",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
    }
}

@Composable
private fun ControlProgressStrip() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("01 Range", "02 Lines", "03 Snapshot", "04 Confirm").forEach { step ->
                Text(step, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ControlSummary(
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
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "Submission brief",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                context.selectedDraft.type,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(context.selectedRange, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            Text(context.selectedDraft.days, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            RemittanceSnapshotReview(draft = context.selectedDraft, compact = true)
            Spacer(Modifier.weight(1f))
            Text("Method", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
            Text(
                context.state.method.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                context.state.note,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
    }
}
