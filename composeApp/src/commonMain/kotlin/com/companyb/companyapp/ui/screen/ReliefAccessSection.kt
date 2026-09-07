package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.ReliefAccessViewModel

/**
 * #357 — the relief-access surface on the session dashboard, both sides of the broadcast
 * model (the #352 rules: targeting retired, multiple relief workers allowed):
 *
 * - **Incoming** (branch members): every PENDING row shows Grant / Deny, plus Cancel for
 *   a pending ask no one wants left standing.
 * - **Outgoing** (the caller's own rows): outcome chips — PENDING waits with Withdraw,
 *   GRANTED reads "active until 04:00 (Manila)", DENIED/CANCELLED inform. Re-asking is
 *   possible after any non-granted outcome while no live request stands.
 *
 * Visibility rule: the card renders only for relief users or callers with an incoming
 * pending row (server-side row scoping backs this).
 */
@Composable
fun ReliefAccessCard(
    viewModel: ReliefAccessViewModel,
    branchDayId: String,
    currentUserId: String?,
    isReliefUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val freshest by viewModel.freshestRequests.collectAsState()
    val grantState by viewModel.grantResult.collectAsState()
    val denyState by viewModel.denyResult.collectAsState()
    val cancelState by viewModel.cancelResult.collectAsState()

    val rows = freshest.orEmpty()
    val incomingPending = rows.incomingPending(currentUserId)
    val outgoing = rows.outgoing(currentUserId)

    if (!isReliefUser && incomingPending.isEmpty()) return

    // Surface action failures inline (the invite screens' inline-error shape); actions write
    // Unit states, so the keep-last list itself never drops to Error for them.
    val actionError =
        listOfNotNull(
            grantState as? UiState.Error,
            denyState as? UiState.Error,
            cancelState as? UiState.Error,
        ).firstOrNull()?.message

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        SectionTitle("Relief access")

        if (incomingPending.isNotEmpty()) {
            IncomingSection(
                viewModel,
                branchDayId,
                incomingPending,
                busy =
                    grantState is UiState.Loading || denyState is UiState.Loading,
            )
        }

        if (isReliefUser) {
            OutgoingSection(
                outgoing,
                cancelState = cancelState,
                onCancel = { row -> viewModel.cancel(row.id, branchDayId) },
            )
        }
    }

    // Surface action failures once (the invite screens' inline-error shape).
    if (actionError != null) {
        Text(
            text = actionError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun IncomingSection(
    viewModel: ReliefAccessViewModel,
    branchDayId: String,
    incomingPending: List<ReliefAccessResponse>,
    busy: Boolean,
) {
    CardSection {
        incomingPending.forEach { row ->
            IncomingRequestRow(
                row = row,
                busy = busy,
                onGrant = { viewModel.grantAccess(row.id, branchDayId) },
                onDeny = { viewModel.denyAccess(row.id, branchDayId) },
                onCancel = { viewModel.cancel(row.id, branchDayId) },
            )
        }
    }
}

@Composable
private fun OutgoingSection(
    outgoing: List<ReliefAccessResponse>,
    cancelState: UiState<Unit>,
    onCancel: (ReliefAccessResponse) -> Unit,
) {
    CardSection {
        outgoing.forEach { row ->
            OutgoingRequestRow(row)
            if (row.requestStatus == ReliefAccessStatus.PENDING) {
                TextButton(
                    enabled = cancelState !is UiState.Loading,
                    onClick = { onCancel(row) },
                ) {
                    Text("Withdraw request")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = InkSubtle,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
}

@Composable
private fun CardSection(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        shape = RoundedCornerShape(CornerRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            content()
        }
    }
}
