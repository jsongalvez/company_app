package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.MemberAttendanceResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.AttendanceRosterViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #404 — the member-marked attendance surface on the session dashboard (both hosts): the
 * branch's home-member roster in Branch Slot order, each row toggling between present and
 * absent. The backend's membership gate is authoritative — a 403 (or any load failure with
 * nothing loaded yet) renders nothing, so relief users and outsiders never see the card.
 */
@Composable
fun AttendanceRosterCard(
    viewModel: AttendanceRosterViewModel,
    branchId: String,
    currentUserId: String?,
    isReliefUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val freshest by viewModel.freshestRoster.collectAsState()
    val markState by viewModel.markResult.collectAsState()

    LaunchedEffect(branchId) {
        viewModel.load(branchId)
    }

    if (isReliefUser) return
    val rows = freshest.orEmpty()
    // Silent exit while unauthorized or not yet loaded — the card only exists for members.
    if (rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        SectionHeader(rows)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            shape = RoundedCornerShape(CornerRadius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                rows.forEach { row ->
                    RosterRow(
                        row = row,
                        canToggle = AttendanceRosterLogic.canToggle(row, currentUserId),
                        busy = markState is UiState.Loading,
                        onToggle = { viewModel.mark(branchId, row.userId, present = !row.present) },
                    )
                }
            }
        }
    }

    // Surface mark failures once, inline (the relief-access action-error shape).
    val markError = markState as? UiState.Error
    if (markError != null) {
        Text(
            text = markError.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun SectionHeader(rows: List<MemberAttendanceResponse>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Attendance",
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = AttendanceRosterLogic.summaryLine(rows).orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = InkSubtle,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
}

@Composable
private fun RosterRow(
    row: MemberAttendanceResponse,
    canToggle: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.displayName, style = MaterialTheme.typography.bodyMedium)
        if (canToggle) {
            TextButton(enabled = !busy, onClick = onToggle) {
                Text(AttendanceRosterLogic.toggleLabel(row))
            }
        } else {
            Text(
                text = if (row.present) "Present" else "Absent",
                style = MaterialTheme.typography.labelMedium,
                color = InkSubtle,
            )
        }
    }
}
