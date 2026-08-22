package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

/**
 * Row renderers and list selectors for the relief-access surface (#351), split out of
 * ReliefAccessSection.kt to keep both files under the detekt file-function budget.
 */

internal fun List<ReliefAccessResponse>.incomingPending(currentUserId: String?) =
    filter { it.targetUser == currentUserId && it.requestStatus == ReliefAccessStatus.PENDING }

internal fun List<ReliefAccessResponse>.outgoing(currentUserId: String?) = filter { it.requestedBy == currentUserId }

/** A pending request targeting the caller — explicit Grant / Deny choices (BR). */
@Composable
internal fun IncomingRequestRow(
    row: ReliefAccessResponse,
    busy: Boolean,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Edit access requested",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "From ${shortId(row.requestedBy)} — they could sign in clients, add practitioners, " +
                        "add product sales, and receive commission splits until 04:00.",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedButton(onClick = onDeny, enabled = !busy) { Text("Deny") }
            Button(onClick = onGrant, enabled = !busy) { Text("Grant") }
        }
    }
}

/** The relief requester's own row — outcome per BR: waits, active-until-04:00, or informed denial. */
@Composable
internal fun OutgoingRequestRow(row: ReliefAccessResponse) {
    val targetName = shortId(row.targetUser)
    when (row.requestStatus) {
        ReliefAccessStatus.PENDING -> {
            RequestLine("Waiting for $targetName's response", InkSubtle)
        }

        ReliefAccessStatus.GRANTED -> {
            RequestLine(
                "Edit access granted — active until 04:00 (Asia/Manila). A new day needs a new request.",
                MaterialTheme.colorScheme.primary,
            )
        }

        ReliefAccessStatus.DENIED -> {
            RequestLine("Denied. You can ask another checked-in user.", MaterialTheme.colorScheme.error)
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
