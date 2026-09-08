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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #670 — shared inline status: updating, last-updated/stale, failure + Retry, success.
 *
 * Never replaces a populated region for a background refresh and never steals focus:
 * the Retry control is reachable by keyboard but never auto-focused. Status always pairs
 * text (or a distinct icon + text) — never color alone — and success is a short nonmodal
 * note with no automatic navigation.
 *
 * No composed entrance animation is added; the UPDATING spinner is essential progress
 * indication (not decorative motion), so reduced motion has nothing to suppress here.
 */
enum class InlineStatusKind {
    UPDATING,
    STALE,
    FAILURE,
    SUCCESS,
    INFO,
}

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
 * Cold-load placeholder: bounded (never a full-screen takeover for a refresh), stable
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

/**
 * One surface per conceptual group: a quiet band for non-fatal context such as
 * last-updated/stale notes that should not read as an error card.
 */
@Composable
fun StatusBand(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
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
