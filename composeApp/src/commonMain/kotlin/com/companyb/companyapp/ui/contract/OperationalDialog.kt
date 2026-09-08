package com.companyb.companyapp.ui.contract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #670 — shared dialog shell.
 *
 * Max [OperationalUiContract.dialogMaxWidth] wide ([Modifier.widthIn], AlertDialog's own
 * window honors it; [OperationalUiContract.dialogWidthFor] pins the same rule as the
 * tested pure decision), viewport-inset on compact screens via the horizontal gutter,
 * scrolling body with fixed title/actions. Initial focus goes to the first meaningful
 * field ([contentFocus] when provided) or to the safe action in a destructive
 * confirmation; Escape/Back cancels only when [OperationalUiContract.canDismissWhileBusy]
 * allows it. On close the platform restores focus to the invoking control while it
 * survives; callers whose confirm removes the invoker (landed revoke) refocus the
 * nearest surviving control explicitly.
 *
 * Tab stays inside via AlertDialog's modal focus. No composed entrance/stagger/scale
 * animation is added — the shell contributes no AnimatedVisibility or animate* APIs,
 * so reduced motion has nothing to suppress here.
 */
@Composable
@Suppress("LongParameterList") // #670 declarative-UI dialog shell stays whole per #535.
fun OperationalDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    isBusy: Boolean = false,
    isDestructive: Boolean = false,
    allowCancelWhenBusy: Boolean = false,
    confirmEnabled: Boolean = true,
    contentFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    val target =
        OperationalUiContract.initialFocusTarget(
            hasFields = contentFocus != null,
            isDestructive = isDestructive,
        )
    LaunchedEffect(target) {
        when (target) {
            DialogFocusTarget.FIRST_FIELD -> contentFocus?.requestFocus()
            DialogFocusTarget.SAFE_ACTION -> confirmFocus.requestFocus()
        }
    }
    // Destructive confirmations focus the safe action: the dismiss control.
    val dismissFocus = if (isDestructive) confirmFocus else null
    AlertDialog(
        onDismissRequest = {
            if (OperationalUiContract.canDismissWhileBusy(isBusy, allowCancelWhenBusy)) onDismiss()
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = OperationalDialogDefaults.BODY_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .padding(end = Spacing.xs),
            ) {
                content()
            }
        },
        confirmButton = {
            if (isDestructive) {
                DestructiveActionButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    isBusy = isBusy,
                )
            } else {
                PrimaryActionButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    isBusy = isBusy,
                )
            }
        },
        dismissButton = {
            val dismissModifier = if (dismissFocus != null) Modifier.focusRequester(dismissFocus) else Modifier
            TertiaryActionButton(
                label = dismissLabel,
                onClick = onDismiss,
                enabled = OperationalUiContract.canDismissWhileBusy(isBusy, allowCancelWhenBusy),
                modifier = dismissModifier,
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = OperationalUiContract.dialogMaxWidth)
                .padding(horizontal = Spacing.md),
        shape = MaterialTheme.shapes.medium,
    )
}

/**
 * Destructive intent stays quiet-tertiary in shape but carries the error token and the
 * same reserved 18dp pending slot as every other action: label + bounds persist while
 * busy, duplicate submission is disabled, and the safe action (Cancel) keeps default
 * emphasis so the two are never confusable.
 */
@Composable
fun DestructiveActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBusy: Boolean = false,
) {
    TextButtonDestructive(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isBusy,
        isBusy = isBusy,
    )
}

/**
 * Narrow destructive variant: revoke/remove confirmations where the only safe initial
 * focus is Cancel and the destructive control stays a quiet tertiary (red only for
 * destructive intent is honored by the error-color label below).
 */
@Composable
@Suppress("LongParameterList") // #670 declarative-UI confirm stays whole per #535.
fun DestructiveConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
) {
    OperationalDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        modifier = modifier,
        dismissLabel = dismissLabel,
        isBusy = isBusy,
        isDestructive = true,
        confirmEnabled = confirmEnabled,
        content = {
            Text(body, style = MaterialTheme.typography.bodyMedium)
        },
    )
}

@Composable
private fun TextButtonDestructive(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    isBusy: Boolean,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(OperationalUiContract.progressSlot),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OperationalUiContract.progressSlot),
                        strokeWidth = OperationalUiContract.focusRingWidth,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.width(OperationalDialogButtonDefaults.LABEL_GAP))
            Text(label, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private object OperationalDialogButtonDefaults {
    val LABEL_GAP = Spacing.xs
}

private object OperationalDialogDefaults {
    val BODY_MAX_HEIGHT = Spacing.xxl * BODY_HEIGHT_FACTOR

    private const val BODY_HEIGHT_FACTOR = 8f
}
