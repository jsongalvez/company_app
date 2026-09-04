package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.UserSlotRow

// #135 D4 (desktop) — slot-order manager: up/down arrows per row, one move = one pairwise swap
// with the neighbor (`POST /slots/swap`, both users actively assigned at the branch); an Edit
// button provides the manual number fallback (`PATCH slot`, shared EditSlotDialog). Rows arrive
// slot ASC (display name tiebreak); deactivated rows dimmed with controls disabled (D2). The
// swap pair disables when EITHER endpoint is deactivated — a swap with an inactive user can only
// be rejected by the backend, so the UI never offers it. Per #95 this desktop-only surface
// (arrows) must not leak to mobile — the android actual is tap-to-edit.
@Composable
actual fun UserSlotOrderList(
    branchName: String,
    rows: List<UserSlotRow>,
    mutationsDisabled: Boolean,
    callbacks: SlotOrderCallbacks,
    errors: List<String>,
) {
    UserSlotOrderCard(
        branchName = branchName,
        isEmpty = rows.isEmpty(),
        errors = errors,
    ) {
        rows.forEachIndexed { index, row ->
            val editable = !row.isDeactivated && !mutationsDisabled
            val canSwapUp =
                editable && index > 0 && !rows[index - 1].isDeactivated
            val canSwapDown =
                editable && index < rows.lastIndex && !rows[index + 1].isDeactivated
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Whole-row dimming for deactivated users — the android actual dims the
                        // row too; the two platforms must not diverge (pass-1 P1 SOFT).
                        .alpha(if (row.isDeactivated) DEACTIVATED_ROW_ALPHA else 1f)
                        .padding(vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = "#${row.slot}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                // Swap with the neighbor above (▲) / below (▼); first/last rows get a single
                // direction. Buttons always render (stable column footprint); disabled while any
                // mutation is in flight, the row is inactive, or a neighbor is inactive — a swap
                // pair with an inactive user can only be rejected by the backend, so the UI never
                // offers it.
                if (index > 0) {
                    TextButton(
                        onClick = { callbacks.onSwap(rows[index - 1].assignmentId, row.assignmentId) },
                        enabled = canSwapUp,
                    ) {
                        Text("▲")
                    }
                }
                if (index < rows.lastIndex) {
                    TextButton(
                        onClick = { callbacks.onSwap(row.assignmentId, rows[index + 1].assignmentId) },
                        enabled = canSwapDown,
                    ) {
                        Text("▼")
                    }
                }
                TextButton(
                    onClick = { callbacks.onEditSlot(row) },
                    enabled = editable,
                ) {
                    Text("Edit")
                }
            }
        }
    }
}
