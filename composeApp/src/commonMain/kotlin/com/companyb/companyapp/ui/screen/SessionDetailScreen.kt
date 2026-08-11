package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #147 mobile SessionDetail route. The dashboard passes the enriched row via nav args
 * (no session-detail GET endpoint exists — the #146-corrected fact), so the content is
 * the same [SessionDetailContent] the desktop inline pane renders.
 *
 * The Notifications call site navigates with sessionId only (row = null) — it renders the
 * limited state below. A real session-detail GET + its read gate is the notifications
 * chain's own build (fog, tracked on the map).
 */
@Composable
fun SessionDetailScreen(
    row: DashboardSessionResponse?,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // #113 content-level Back precedent (pushed-route topbar pattern stays fog).
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }
        if (row == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.md),
            ) {
                Text(
                    text = "Session detail",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "This session's details aren't available from here yet — " +
                            "open it from today's dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        } else {
            SessionDetailContent(session = row)
        }
    }
}
