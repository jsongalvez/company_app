package com.companyb.companyapp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun DayStatusWarning(dayStatus: DayStatus) {
    val message =
        when (dayStatus) {
            DayStatus.PAST -> "Past branch day: only Coordinators can edit."
            DayStatus.REMITTED -> "Remitted branch day: Coordinator edits require a reason."
            DayStatus.OPEN -> null
        }
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
