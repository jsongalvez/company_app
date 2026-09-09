package com.companyb.companyapp.ui.contract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.companyb.companyapp.ui.theme.Spacing

@Composable
fun InlineStatus(
    message: String,
    kind: InlineStatusKind,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
) {
    val textColor =
        when (kind) {
            InlineStatusKind.FAILURE -> MaterialTheme.colorScheme.error
            InlineStatusKind.STALE -> MaterialTheme.colorScheme.onSurfaceVariant
            InlineStatusKind.SUCCESS -> MaterialTheme.colorScheme.onSurfaceVariant
            InlineStatusKind.UPDATING -> MaterialTheme.colorScheme.onSurfaceVariant
            InlineStatusKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (kind == InlineStatusKind.UPDATING) {
            CircularProgressIndicator(
                modifier = Modifier.size(OperationalUiContract.progressSlot),
                strokeWidth = OperationalUiContract.focusRingWidth,
            )
        }
        Text(
            text = statusPrefix(kind) + message,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TertiaryActionButton(label = retryLabel, onClick = onRetry)
        }
    }
}

/**
 * Cold-load indicator: bounded (never a full-screen takeover for a refresh), stable
 * across recompositions so a landing read does not shift surrounding chrome.
 */
@Composable
fun ColdLoadPlaceholder(
    message: String = "Loading…",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ColdLoadDefaults.MIN_HEIGHT)
                .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusPrefix(kind: InlineStatusKind): String =
    when (kind) {
        InlineStatusKind.STALE -> "Stale — "
        InlineStatusKind.SUCCESS -> "Done — "
        InlineStatusKind.INFO -> "Note — "
        InlineStatusKind.FAILURE -> ""
        InlineStatusKind.UPDATING -> ""
    }

private object ColdLoadDefaults {
    val MIN_HEIGHT = Spacing.xxl + Spacing.xl + Spacing.md
}
