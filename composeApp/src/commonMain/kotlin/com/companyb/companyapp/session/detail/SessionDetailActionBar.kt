package com.companyb.companyapp.session.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.session.dashboard.normalizedReason
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #675 — the detail action region: one stable band shared by the desktop inline pane and
 * the compact pushed detail. Every authorized slot stays mounted during mutations
 * (disabled with stable geometry per the #670 contract — nothing is removed mid-flight
 * while its flow owns the bar): the primary shortcut, the Record sale secondary, and
 * the Session actions menu carrying the remaining legal transitions plus void/unvoid.
 * While any mutation is in flight the whole band disables together — the flows share
 * the row version, so a second dispatch mid-flight would 409 against the just-committed
 * row (the pre-existing gate.mutating semantics, kept deliberately).
 *
 * A completed session keeps a quiet state line in the primary slot, so the just-clicked
 * primary is never replaced by a destructive action at that pointer location.
 */
@Composable
internal fun DetailActionBar(
    model: SessionDetailActionModel,
    showSale: Boolean,
    voidAffordance: SessionVoidAffordance?,
    mutating: Boolean,
    primaryBusy: Boolean,
    saleBusy: Boolean,
    primaryFocus: FocusRequester,
    menuTriggerFocus: FocusRequester,
    saleFocus: FocusRequester,
    onPrimary: (SessionDetailPrimary) -> Unit,
    onStatusOption: (SessionStatus) -> Unit,
    onSell: () -> Unit,
    onVoid: () -> Unit,
    onUnvoid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        when {
            model.primary != null -> {
                PrimaryActionButton(
                    label =
                        when (model.primary) {
                            SessionDetailPrimary.ADD_SELF -> "Add myself"
                            SessionDetailPrimary.COMPLETE -> "Mark completed"
                        },
                    onClick = { onPrimary(model.primary) },
                    enabled = !mutating,
                    isBusy = primaryBusy,
                    modifier = Modifier.focusRequester(primaryFocus),
                )
            }

            model.showCompletedState -> {
                CompletedStateLine()
            }
        }
        ActionMenuRow(
            showSale = showSale,
            showMenu = model.statusMenuOptions.isNotEmpty() || voidAffordance != null,
            mutating = mutating,
            saleBusy = saleBusy,
            menuTriggerFocus = menuTriggerFocus,
            saleFocus = saleFocus,
            onSell = onSell,
            menuContent = { close ->
                model.statusMenuOptions.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.detailActionLabel()) },
                        onClick = {
                            close()
                            onStatusOption(target)
                        },
                    )
                }
                if (model.statusMenuOptions.isNotEmpty() && voidAffordance != null) {
                    HorizontalDivider()
                }
                when (voidAffordance) {
                    SessionVoidAffordance.VOID -> {
                        DropdownMenuItem(
                            text = { Text("Void session…", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                close()
                                onVoid()
                            },
                        )
                    }

                    SessionVoidAffordance.UNVOID -> {
                        DropdownMenuItem(
                            text = { Text("Unvoid session…") },
                            onClick = {
                                close()
                                onUnvoid()
                            },
                        )
                    }

                    null -> {
                        Unit
                    }
                }
            },
        )
    }
}

/** Sale secondary + Session actions menu on one row; the row is omitted when both are absent. */
@Composable
private fun ActionMenuRow(
    showSale: Boolean,
    showMenu: Boolean,
    mutating: Boolean,
    saleBusy: Boolean,
    menuTriggerFocus: FocusRequester,
    saleFocus: FocusRequester,
    onSell: () -> Unit,
    menuContent: @Composable (() -> Unit) -> Unit,
) {
    if (!showSale && !showMenu) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSale) {
            SecondaryActionButton(
                label = "Record sale",
                onClick = onSell,
                enabled = !mutating,
                isBusy = saleBusy,
                modifier = Modifier.focusRequester(saleFocus),
            )
        }
        if (showMenu) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TertiaryActionButton(
                    label = "Session actions",
                    onClick = { menuOpen = true },
                    enabled = !mutating,
                    modifier = Modifier.focusRequester(menuTriggerFocus),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    // #675 — a closed menu returns focus to its trigger (the invoking
                    // control), so keyboard operators stay in the action region.
                    onDismissRequest = {
                        menuOpen = false
                        menuTriggerFocus.requestFocus()
                    },
                ) {
                    // Each item closes the menu first; the pane's terminal effect moves
                    // focus to the primary slot (or nearest surviving action).
                    menuContent { menuOpen = false }
                }
            }
        }
    }
}

/** Quiet completion state occupying the primary slot (same band, never a button). */
@Composable
private fun CompletedStateLine() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ACTION_STATE_MIN_HEIGHT.dp)
                .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Completed",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSubtle,
        )
    }
}

/**
 * #675 — the version-conflict banner: the status PATCH 409ed, so the row was refreshed
 * authoritatively. Reload repaints and clears; open dialog drafts (reason/remarks) are
 * pane-held and survive the refresh, so unsent permitted edits stay for review.
 */
@Composable
internal fun StatusConflictBanner(
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "This session changed — refreshed below. Review and retry.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TertiaryActionButton(label = "Reload", onClick = onReload)
    }
}

/**
 * #675 — the sticky status-failure row: the failed target stays redispatchable with the
 * freshly refreshed version (optimistic locking means the stale version is spent), so
 * failure retains its context instead of clearing with the drain. Retry hides once the
 * target leaves the legal set (e.g. capability revoked by the refresh).
 */
@Composable
internal fun StatusErrorRow(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (canRetry) {
            TertiaryActionButton(label = "Retry", onClick = onRetry)
        }
    }
}

/**
 * #675 — the audit-reason gate for status writes on a REMITTED day (the same server
 * requirement as void/unvoid, hence the shared [ReasonConfirmDialog]). Ordinary
 * completion on an open day dispatches with no dialog.
 */
@Composable
internal fun StatusReasonDialogHost(
    target: SessionStatus?,
    mutating: Boolean,
    onConfirmed: (SessionStatus, String) -> Unit,
    onCleared: () -> Unit,
    // #675 — plain dismissal returns focus to the menu trigger (the invoking control);
    // the confirm path lands in the action region via the terminal focus effect.
    onDismissFocus: () -> Unit = {},
) {
    val confirmed = target ?: return
    ReasonConfirmDialog(
        prompt =
            ReasonPrompt(
                title = "${confirmed.detailActionLabel()}?",
                caption = "Writes on a remitted day require a reason for the audit trail.",
                confirmLabel = confirmed.detailActionLabel(),
                destructive = false,
            ),
        inFlight = mutating,
        onConfirm = { reason ->
            onCleared()
            onConfirmed(confirmed, normalizedReason(reason))
        },
        onDismiss = {
            onCleared()
            onDismissFocus()
        },
    )
}

private const val ACTION_STATE_MIN_HEIGHT = 48
