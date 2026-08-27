package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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

private const val COMMAND_CONTENT_WEIGHT = 1.25f
private const val COMMAND_RAIL_WEIGHT = 0.75f

@Composable
internal fun DashboardPrototypeVariantB(context: DashboardPrototypeContext) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    "B / Command center",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "One focused lens at a time, with a persistent day summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${context.data.unreadNotifications} unread notifications",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        CommandTabs(context)
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(
                modifier =
                    Modifier.weight(COMMAND_CONTENT_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                when (context.state.activeSection) {
                    DashboardPrototypeSection.TODAY -> CommandToday(context)
                    DashboardPrototypeSection.ROSTER -> CommandRoster(context)
                    DashboardPrototypeSection.RELIEF -> CommandRelief(context)
                }
            }
            CommandSummaryRail(context, Modifier.weight(COMMAND_RAIL_WEIGHT))
        }
    }
}

@Composable
private fun CommandTabs(context: DashboardPrototypeContext) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DashboardPrototypeSection.values().forEach { section ->
            val selected = section == context.state.activeSection
            Surface(
                onClick = { context.state.activeSection = section },
                shape =
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(CornerRadius.sm),
                color =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun CommandToday(context: DashboardPrototypeContext) {
    DashboardSectionTitle("Session queue", "Select a row for detail")
    context.data.sessions.forEach { session ->
        DashboardSessionRow(
            session = session,
            selected = session.id == context.selectedSession.id,
            onClick = { context.onSessionSelect(session) },
        )
    }
}

@Composable
private fun CommandRoster(context: DashboardPrototypeContext) {
    DashboardSectionTitle("Attendance roster", "Slots and clock-in state")
    context.data.staff.forEach { staff ->
        DashboardStaffLine(
            staff = staff,
            selected = staff.id == context.selectedStaff.id,
            onClick = { context.onStaffSelect(staff) },
        )
    }
}

@Composable
private fun CommandRelief(context: DashboardPrototypeContext) {
    DashboardSectionTitle("Relief access", "Requests, invites, grants")
    context.data.reliefCards.forEach { card -> DashboardReliefCard(card) }
}

@Composable
private fun CommandSummaryRail(
    context: DashboardPrototypeContext,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Day summary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CommandRailValue("Gross", context.data.completedIncome, "completed")
            CommandRailValue("Commission", context.data.commission, "your split")
            CommandRailValue("Queue", context.data.sessionsLabel, "today")
            CommandRailValue("Unread", context.data.unreadNotifications.toString(), "notifications")
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("Selected", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
            Text(
                context.selectedSession.client,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(context.selectedSession.concern, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            DashboardStatusBadge(context.selectedSession.status)
        }
    }
}

@Composable
private fun CommandRailValue(
    label: String,
    value: String,
    detail: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("  $detail", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
    }
}
