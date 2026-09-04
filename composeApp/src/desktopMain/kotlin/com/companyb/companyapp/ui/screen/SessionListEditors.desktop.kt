package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

// #477 — split from SessionList.desktop.kt (TMF 15/11): the row's edit cells plus the
// shared inline editors (Select/Price/StatusLine, also used by RemittedReasonDialog).
@Composable
internal fun RowScope.DashboardRowEditCells(
    session: DashboardSessionResponse,
    config: DashboardRowConfig,
    actions: DashboardEditActions,
) {
    DashboardEditableCell(session, DashboardEditField.STATUS, Modifier.weight(1f), config, actions)
    // #405 — a medical-mission session is always ₱0 (BR §Session types): no price
    // editor; the disabled clickable passes the tap through to row selection.
    DashboardEditableCell(session, DashboardEditField.FINAL_PRICE, Modifier.weight(1f), config, actions)
}

/**
 * #149 — a table cell for [DashboardEditField]: hover-revealed edit affordance when [canEdit]
 * (Q4 — silent when the capability is revoked), the in-place editor while this cell owns the
 * edit, and the inline error / conflict / changed-remotely line underneath (ADR-0022).
 * The day-gate derives from [config] per field (#425 status corrections, #405 mission lock).
 */
@Composable
private fun DashboardEditableCell(
    session: DashboardSessionResponse,
    field: DashboardEditField,
    modifier: Modifier = Modifier,
    config: DashboardRowConfig,
    actions: DashboardEditActions,
) {
    val edit = config.edit
    val isEditing = edit != null && edit.sessionId == session.id && edit.field == field
    val canEditOnDay =
        config.canEdit && config.dayStatus != null &&
            (config.dayStatus == DayStatus.OPEN || config.canCorrectStatus)
    val canEdit =
        when (field) {
            DashboardEditField.STATUS -> {
                canEditOnDay &&
                    statusEditAllowed(
                        isWalkIn = session.isWalkIn,
                        currentStatus = session.sessionStatus,
                        hasCorrectionAuthority = config.canCorrectStatus,
                        dayStatus = config.dayStatus,
                    )
            }

            DashboardEditField.FINAL_PRICE -> {
                canEditOnDay && !missionPriceLocked(session.sessionType)
            }
        }
    Column(modifier = modifier) {
        when {
            // #403 — a REMITTED day's editor is a dialog collecting the audit reason;
            // the inline cell editors stay untouched for every other day state.
            isEditing && config.requiresReason -> {
                RemittedCellDialog(session, edit, canEdit, config, actions)
            }

            isEditing -> {
                val statusValues =
                    statusOptionsFor(
                        isWalkIn = session.isWalkIn,
                        currentStatus = session.sessionStatus,
                        hasCorrectionAuthority = config.canCorrectStatus,
                        dayStatus = config.dayStatus,
                    )
                val inline = InlineEditActions(actions.onEditDraftChange, actions.onEditCommit, actions.onEditDiscard)
                EditControl(edit = edit, canEdit = canEdit, statusValues = statusValues, actions = inline)
                EditStatusLine(edit = edit, onReload = actions.onEditReload)
            }

            else -> {
                CellDisplay(
                    session = session,
                    field = field,
                    canEdit = canEdit,
                    onCellClick = {
                        // The enabled child clickable consumes the click — the row's own
                        // clickable never sees it, so the selection follows explicitly. Note:
                        // when the switch is blocked (a Model-A/conflict error on the other
                        // cell), the selection still moves while the editor stays put — the
                        // error line + Esc remain the exit.
                        actions.onSessionSelect(session)
                        actions.onEditStart(session.id, field)
                    },
                )
            }
        }
    }
}

@Composable
private fun CellDisplay(
    session: DashboardSessionResponse,
    field: DashboardEditField,
    canEdit: Boolean,
    onCellClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    enabled = canEdit,
                    onClick = onCellClick,
                ),
    ) {
        when (field) {
            DashboardEditField.STATUS -> {
                SessionStatusBadge(session)
            }

            DashboardEditField.FINAL_PRICE -> {
                Text(
                    text = "₱${session.finalPrice}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Q4 — hover-revealed affordance (a disabled clickable passes the click through to
        // the row's selection clickable; the affordance is only hinted on hover).
        if (canEdit && hovered) {
            EditPencil(
                tint = InkSubtle,
                modifier = Modifier.padding(start = Spacing.xxs),
            )
        }
    }
}

@Composable
internal fun EditControl(
    edit: DashboardEditState,
    canEdit: Boolean,
    statusValues: List<String>,
    actions: InlineEditActions,
    autoCommit: Boolean = true,
) {
    when (edit.field) {
        DashboardEditField.STATUS -> {
            SelectEditor(statusValues, edit, actions, autoCommit = autoCommit, canEdit = canEdit)
        }

        DashboardEditField.FINAL_PRICE -> {
            PriceEditor(edit, actions, autoCommit = autoCommit, canEdit = canEdit)
        }
    }
}

// #403 — the REMITTED-day branch of [DashboardEditableCell]: the value control without
// auto-commit plus the audit-reason dialog shell (shared with RemittedReasonDialog's
// Confirm-owned commit via EditControl's autoCommit flag).
@Composable
private fun RemittedCellDialog(
    session: DashboardSessionResponse,
    edit: DashboardEditState,
    canEdit: Boolean,
    config: DashboardRowConfig,
    actions: DashboardEditActions,
) {
    RemittedReasonDialog(
        edit = edit,
        canEdit = canEdit,
        statusValues =
            statusOptionsFor(
                isWalkIn = session.isWalkIn,
                currentStatus = session.sessionStatus,
                hasCorrectionAuthority = config.canCorrectStatus,
                dayStatus = config.dayStatus,
            ),
        actions =
            RemittedEditActions(
                onDraftChange = actions.onEditDraftChange,
                onReasonChange = actions.onEditReasonChange,
                onCommit = actions.onEditCommit,
                onDiscard = actions.onEditDiscard,
                onReload = actions.onEditReload,
            ),
    )
}

/**
 * Q4 per-control commit: the dropdown commits on select (the #142 GenderFieldEditor shape).
 * Selecting the current value again commits an unchanged draft — the VM exits without a request.
 * Esc (menu closed) discards — the Model-A failed editor's sanctioned exit.
 * #403 — shared with [RemittedReasonDialog], where [autoCommit] is off.
 */
@Composable
internal fun SelectEditor(
    values: List<String>,
    edit: DashboardEditState,
    actions: InlineEditActions,
    autoCommit: Boolean = true,
    canEdit: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = edit.draft,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = canEdit && !edit.inFlight,
            isError = edit.error != null,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canEdit && !edit.inFlight) { menuOpen = true }
                    .onPreviewKeyEvent {
                        // Menu-open Esc is consumed by the popup (dismiss); only a
                        // menu-closed Esc discards the edit.
                        if (it.key == Key.Escape && (!menuOpen || !canEdit)) {
                            actions.onDiscard()
                            true
                        } else {
                            false
                        }
                    },
        )
        if (edit.inFlight) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Spacing.xs)
                        .size(EDIT_SPINNER_SIZE),
                strokeWidth = EDIT_SPINNER_STROKE,
            )
        }
        DropdownMenu(
            expanded = menuOpen && canEdit,
            onDismissRequest = { menuOpen = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    enabled = canEdit,
                    onClick = {
                        menuOpen = false
                        actions.onDraftChange(value)
                        // #403 — inside the REMITTED-day dialog the Confirm button owns the
                        // commit (the reason must be collected first).
                        if (autoCommit) actions.onCommit()
                    },
                )
            }
        }
    }
}

/** Q4 — price commits on Enter/blur; Esc discards (#142 editor shape + the VM's in-flight guard). */
@Composable
internal fun PriceEditor(
    edit: DashboardEditState,
    actions: InlineEditActions,
    autoCommit: Boolean = true,
    canEdit: Boolean = true,
) {
    OutlinedTextField(
        value = edit.draft,
        onValueChange = actions.onDraftChange,
        singleLine = true,
        enabled = canEdit && !edit.inFlight,
        isError = edit.error != null,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = { if (canEdit && autoCommit) actions.onCommit() },
            ),
        trailingIcon = {
            if (edit.inFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(EDIT_SPINNER_SIZE),
                    strokeWidth = EDIT_SPINNER_STROKE,
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                // Blur-commit (Q4); the VM's in-flight guard absorbs the dispose-time blur,
                // and a conflict-state blur must not re-dispatch a doomed stale-version
                // PATCH (pass-1 finding: it swallowed the first Reload click).
                // #403 — the dialog's reason field takes focus, so there autoCommit is off
                // and the Confirm button owns the commit.
                .onFocusChanged {
                    if (canEdit && !it.isFocused) {
                        if (!edit.conflict && autoCommit) actions.onCommit()
                    }
                }.onPreviewKeyEvent {
                    if (it.key == Key.Escape) {
                        actions.onDiscard()
                        true
                    } else {
                        false
                    }
                },
    )
}

/** Model-A inline error; the 409 conflict adds its Reload action; post-reload marks remote changes. */
@Composable
internal fun EditStatusLine(
    edit: DashboardEditState,
    onReload: () -> Unit,
) {
    val error = edit.error
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
        if (edit.conflict) {
            TextButton(
                onClick = onReload,
                modifier = Modifier.padding(top = Spacing.xxs),
            ) {
                Text("Reload", style = MaterialTheme.typography.labelSmall)
            }
        }
    } else if (edit.fieldChangedRemotely) {
        // ADR-0022 "indicate changed fields" — the reload landed and the committed value
        // differs from the attempted draft.
        Text(
            text = "Changed by someone else",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/** Compact pencil glyph — Canvas-drawn (no material-icons dependency, the #107/#142 precedent). */
@Suppress("MagicNumber")
@Composable
private fun EditPencil(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .size(12.dp)
                .rotate(-45f),
    ) {
        val w = size.width
        val h = size.height
        val bodyH = h * 0.30f
        val bodyTop = (h - bodyH) / 2f
        val tipLen = w * 0.25f
        val eraserLen = w * 0.15f

        drawRect(
            color = tint,
            topLeft = Offset(0f, bodyTop),
            size = Size(eraserLen, bodyH),
        )
        drawRect(
            color = tint,
            topLeft = Offset(eraserLen, bodyTop),
            size = Size(w - eraserLen - tipLen, bodyH),
        )
        drawLine(
            color = tint,
            start = Offset(w - eraserLen - tipLen, bodyTop),
            end = Offset(w - eraserLen, h / 2f),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = tint,
            start = Offset(w - eraserLen - tipLen, bodyTop + bodyH),
            end = Offset(w - eraserLen, h / 2f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private val EDIT_SPINNER_SIZE = 12.dp
private val EDIT_SPINNER_STROKE = 2.dp
