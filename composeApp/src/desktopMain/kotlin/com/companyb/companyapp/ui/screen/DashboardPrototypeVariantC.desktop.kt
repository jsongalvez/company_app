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
internal fun DashboardPrototypeVariantC(context: DashboardPrototypeContext) {
    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TeamDirectory(context, Modifier.width(300.dp).fillMaxHeight())
        StaffWorkspace(context, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun TeamDirectory(
    context: DashboardPrototypeContext,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "C / Team workspace",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("Roster-first navigation", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DashboardSectionTitle("On duty", "${context.data.staff.size} staff")
            context.data.staff.forEach { staff ->
                DashboardStaffLine(
                    staff = staff,
                    selected = staff.id == context.selectedStaff.id,
                    onClick = { context.onStaffSelect(staff) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Relief updates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            context.data.reliefCards.forEach { card ->
                Text(
                    card.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    card.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StaffWorkspace(
    context: DashboardPrototypeContext,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        WorkspaceHeader(context)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            DashboardMetric(
                "Sessions",
                "${context.selectedStaff.sessionCount}",
                context.selectedStaff.attendance,
                Modifier.weight(1f),
            )
            DashboardMetric("Shift", context.selectedStaff.shift, context.selectedStaff.slot, Modifier.weight(1f))
            DashboardMetric(
                "Unread",
                context.data.unreadNotifications.toString(),
                "branch mailbox",
                Modifier.weight(1f),
            )
        }
        WorkspaceSessions(context)
        WorkspaceRelief(context)
    }
}

@Composable
private fun WorkspaceHeader(context: DashboardPrototypeContext) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardAvatar(context.selectedStaff.initials, selected = true)
            Column(modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    context.selectedStaff.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${context.selectedStaff.role} / ${context.selectedStaff.attendance} / " +
                        context.selectedStaff.shift,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${context.selectedStaff.sessionCount} assigned",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun WorkspaceSessions(context: DashboardPrototypeContext) {
    DashboardSectionTitle("Assigned sessions", "Click any row to inspect")
    val assigned = context.data.sessions.filter { it.practitioner == context.selectedStaff.name }
    if (assigned.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "No assigned sessions yet. This keeps roster selection useful on sparse days.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    } else {
        assigned.forEach { session ->
            DashboardSessionRow(
                session = session,
                selected = session.id == context.selectedSession.id,
                onClick = { context.onSessionSelect(session) },
            )
        }
    }
}

@Composable
private fun WorkspaceRelief(context: DashboardPrototypeContext) {
    DashboardSectionTitle("Branch relief context", "${context.data.reliefCards.size} updates")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        context.data.reliefCards.forEach { card -> DashboardReliefCard(card) }
    }
}
