package com.companyb.companyapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #113 D7/D8 — route gate card. Drawer hides the gated item without `EDIT_BRANCH_DATA` (#108
 * DrawerViewModel), so this only renders on a direct nav; the code-only gate is the #156
 * any-context check (#92 Q3), with the backend's GLOBAL gate (F5) as the authoritative
 * backstop (ADR-0007). #92's `UiState.Unauthorized` card is an unimplemented lock —
 * this minimal card is the in-place 403 surface per D8.
 *
 * Moved from `ui.screen.ClientsScreen` to the shared `ui` primitive owner (#554): the card
 * is consumed by navigation graphs across features, not by the clients feature alone.
 */
@Composable
fun RouteGateCard(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "You don't have permission to view $label",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
