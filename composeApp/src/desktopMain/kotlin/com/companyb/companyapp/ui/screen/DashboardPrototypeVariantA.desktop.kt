package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

private const val PULSE_SESSION_WEIGHT = 1.25f
private const val PULSE_CONTEXT_WEIGHT = 0.75f

@Composable
internal fun DashboardPrototypeVariantA(context: DashboardPrototypeContext) {
    val data = context.data
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                "A / Pulse board",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "A glanceable flow: income first, live sessions next, team context beside it.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric("Gross income", data.completedIncome, "Completed, non-voided", Modifier.weight(1f))
            DashboardMetric("Your commission", data.commission, "Product split today", Modifier.weight(1f))
            DashboardMetric(
                "Sessions",
                data.sessionsLabel,
                "Across ${data.staff.count { it.attendance != "Not clocked in" }} on duty",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
            PulseSessionColumn(context, Modifier.weight(PULSE_SESSION_WEIGHT))
            PulseContextColumn(context, Modifier.weight(PULSE_CONTEXT_WEIGHT))
        }
    }
}

@Composable
private fun PulseSessionColumn(
    context: DashboardPrototypeContext,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DashboardSectionTitle("Today's flow", "${context.data.sessions.size} appointments")
        if (context.data.sessions.isEmpty()) {
            PulseEmptyNotice()
        } else {
            context.data.sessions.forEach { session ->
                DashboardSessionRow(
                    session = session,
                    selected = session.id == context.selectedSession.id,
                    onClick = { context.onSessionSelect(session) },
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text("Selected session", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
                Text(
                    "${context.selectedSession.client} / ${context.selectedSession.concern}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${context.selectedSession.time} / ${context.selectedSession.finalPrice} / " +
                        if (context.selectedSession.booked) "Booked" else "Walk-in",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
        }
    }
}

@Composable
private fun PulseEmptyNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                "No sessions scheduled",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "This sparse dataset keeps the dashboard's empty shape visible for review.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun PulseContextColumn(
    context: DashboardPrototypeContext,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DashboardSectionTitle("On the floor", "${context.data.staff.size} people")
                context.data.staff.forEach { staff ->
                    DashboardStaffLine(
                        staff = staff,
                        selected = staff.id == context.selectedStaff.id,
                        onClick = { context.onStaffSelect(staff) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DashboardSectionTitle("Relief inbox", "${context.data.reliefCards.size} updates")
            context.data.reliefCards.forEach { card -> DashboardReliefCard(card) }
        }
    }
}
