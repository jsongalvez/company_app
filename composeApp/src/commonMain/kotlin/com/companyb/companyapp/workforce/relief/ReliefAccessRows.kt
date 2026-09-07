package com.companyb.companyapp.workforce.relief

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.contracts.workforce.ReliefAccessResponse
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

/**
 * Row renderers and list selectors for the relief-access surface (#357 broadcast model),
 * split out of ReliefAccessSection.kt to keep both files under the detekt file-function
 * budget.
 */

internal fun List<ReliefAccessResponse>.incomingPending(currentUserId: String?) =
    filter { it.requestedBy != currentUserId && it.requestStatus == ReliefAccessStatus.PENDING }

internal fun List<ReliefAccessResponse>.outgoing(currentUserId: String?) = filter { it.requestedBy == currentUserId }

/** A pending request from an outsider — explicit Grant / Deny choices (the #352 broadcast). */
@Composable
internal fun IncomingRequestRow(
    row: ReliefAccessResponse,
    busy: Boolean,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Relief duty requested",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "From ${shortId(row.requestedBy)} — granting lets them sign in clients, add practitioners, " +
                        "add product sales, and receive commission splits until 04:00.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel request") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedButton(onClick = onDeny, enabled = !busy) { Text("Deny") }
            Button(onClick = onGrant, enabled = !busy) { Text("Grant") }
        }
    }
}

/**
 * The requester's own row — outcome per the #352 rules: waits, active-until-04:00, or
 * informed of denial/cancel; every non-granted outcome may be re-asked.
 */
@Composable
internal fun OutgoingRequestRow(row: ReliefAccessResponse) {
    when (row.requestStatus) {
        ReliefAccessStatus.PENDING -> {
            RequestLine("Waiting for the branch's response", InkSubtle)
        }

        ReliefAccessStatus.GRANTED -> {
            RequestLine(
                "Edit access granted — active until 04:00 (Asia/Manila). A new day needs a new request.",
                MaterialTheme.colorScheme.primary,
            )
        }

        ReliefAccessStatus.DENIED -> {
            RequestLine("Denied. You can ask again.", MaterialTheme.colorScheme.error)
        }

        ReliefAccessStatus.CANCELLED -> {
            RequestLine("Cancelled. You can ask again.", InkSubtle)
        }
    }
}

@Composable
private fun RequestLine(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

private fun shortId(id: String): String = id.take(SHORT_ID_LENGTH)

private const val SHORT_ID_LENGTH = 8
