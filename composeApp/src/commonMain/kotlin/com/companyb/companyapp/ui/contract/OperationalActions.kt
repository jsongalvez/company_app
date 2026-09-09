package com.companyb.companyapp.ui.contract

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.ui.theme.PrimaryHover
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #670 — semantic action hierarchy with stable pending geometry.
 *
 * One filled primary per task region; secondary outlined/quiet; tertiary text. A busy
 * action keeps its label and outer bounds: the [OperationalUiContract.progressSlot] slot is
 * always reserved (invisible reserved slot when idle), duplicate submission is disabled, and
 * adjacent controls never shift. Minimum height covers touch (48dp); desktop pointer
 * targets (40dp) are satisfied inside it.
 *
 * No entrance/stagger/scale animation; Material's instant state change is the behavior.
 */
@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    StableActionRow(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = OperationalUiContract.isActionEnabled(enabled, isBusy),
        isBusy = isBusy,
        isPrimary = true,
    )
}

@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    StableActionRow(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = OperationalUiContract.isActionEnabled(enabled, isBusy),
        isBusy = isBusy,
        isPrimary = false,
    )
}

@Composable
fun TertiaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .defaultMinSize(
                    minWidth = OperationalUiContract.minTargetTouch,
                    minHeight = OperationalUiContract.minTargetTouch,
                ).operationalFocusRing(),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StableActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    isBusy: Boolean,
    isPrimary: Boolean,
) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(OperationalUiContract.progressSlot),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OperationalUiContract.progressSlot),
                        strokeWidth = OperationalUiContract.focusRingWidth,
                        color =
                            if (isPrimary) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
            }
            Spacer(modifier = Modifier.width(OperationalButtonDefaults.LABEL_GAP))
            // #670 — long labels ellipsize inside the stable bounds; adjacent controls stay fixed.
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val stableModifier =
        modifier
            .defaultMinSize(minHeight = OperationalUiContract.minActionHeight)
            .operationalFocusRing()
    if (isPrimary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = stableModifier,
            shape = MaterialTheme.shapes.small,
            contentPadding = OperationalButtonDefaults.CONTENT_PADDING,
            content = { content() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = stableModifier,
            shape = MaterialTheme.shapes.small,
            contentPadding = OperationalButtonDefaults.CONTENT_PADDING,
            content = { content() },
        )
    }
}

private object OperationalButtonDefaults {
    val LABEL_GAP = Spacing.xs
    val CONTENT_PADDING = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)
}

/**
 * Visible 2dp focus indicator distinct from selection/hover: a [PrimaryHover] outline
 * drawn only while focused. Hover keeps rowHover-style washes; selection keeps fills.
 */
fun Modifier.operationalFocusRing(): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(
                        BorderStroke(OperationalUiContract.focusRingWidth, PrimaryHover),
                        MaterialTheme.shapes.small,
                    )
                } else {
                    Modifier
                },
            )
    }

/**
 * Minimum touch target guard for icon-class controls: 48x48 with 18–20dp glyphs.
 * Callers supply accessible names via contentDescription at the Icon call site.
 */
fun Modifier.operationalTouchTarget(): Modifier =
    this.defaultMinSize(
        minWidth = OperationalUiContract.minTargetTouch,
        minHeight = OperationalUiContract.minTargetTouch,
    )
