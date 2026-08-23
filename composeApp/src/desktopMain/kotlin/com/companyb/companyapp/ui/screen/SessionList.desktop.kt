package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing

// #97 Q1 — desktop table: 6 primary columns (booked time, client + walk-in dot, type, status,
// final price, VOIDED slot right-aligned 22%); secondary fields live in the detail pane
// (master-detail Row, #91). Practitioners demoted to the pane (Q1 pressure-test lock).
@Composable
internal actual fun SessionList(
    args: SessionListArgs,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        TableHeaderRow(onRefresh = args.onRefresh)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            args.sessions.forEach { session ->
                DashboardTableRow(
                    session = session,
                    isSelected = session.id == args.selectedSessionId,
                    onClick = { args.onSessionClick(session) },
                    onSessionSelect = args.onSessionClick,
                    canEdit = args.canEdit,
                    edit = args.edit,
                    onEditStart = args.onEditStart,
                    onEditDraftChange = args.onEditDraftChange,
                    onEditCommit = args.onEditCommit,
                    onEditDiscard = args.onEditDiscard,
                    onEditReload = args.onEditReload,
                    requiresReason = args.requiresReason,
                    onEditReasonChange = args.onEditReasonChange,
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow(onRefresh: () -> Unit) {
    // Right-aligned action row (pass-2: the Refresh button must NOT occupy the VOIDED
    // column — the spec's 22% slot is data, not chrome). #150 — shared with the desktop
    // empty state (DashboardEmptyState.desktop.kt): the affordance-position stability
    // between the empty and list states rests on this single copy.
    DashboardRefreshRow(onRefresh)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        TableCell("Booked", modifier = Modifier.weight(1f), header = true)
        TableCell("Client", modifier = Modifier.weight(2f), header = true)
        TableCell("Type", modifier = Modifier.weight(1f), header = true)
        TableCell("Status", modifier = Modifier.weight(1f), header = true)
        TableCell("Final", modifier = Modifier.weight(1f), header = true)
        // Q3 — VOIDED slot: 22% of the row width, right-aligned separation from the price.
        Box(
            modifier =
                Modifier
                    .weight(DESKTOP_VOIDED_SLOT_WEIGHT)
                    .padding(end = Spacing.xs),
            contentAlignment = Alignment.CenterEnd,
        ) {}
    }
}

/** #150 — the right-aligned Refresh row shared by the table header and the desktop empty state. */
@Composable
internal fun DashboardRefreshRow(onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Text("Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DashboardTableRow(
    session: DashboardSessionResponse,
    isSelected: Boolean,
    onClick: () -> Unit,
    canEdit: Boolean,
    edit: DashboardEditState?,
    onSessionSelect: (DashboardSessionResponse) -> Unit,
    onEditStart: (String, DashboardEditField) -> Unit,
    onEditDraftChange: (String) -> Unit,
    onEditCommit: () -> Unit,
    onEditDiscard: () -> Unit,
    onEditReload: () -> Unit,
    requiresReason: Boolean,
    onEditReasonChange: (String) -> Unit,
) {
    // Q3 — voided row: Danger 22% alpha over Surface1; selection uses the surface-3 slot.
    val rowBackground =
        when {
            session.isVoided -> {
                MaterialTheme.colorScheme.error.copy(alpha = VOIDED_ROW_ALPHA)
            }

            isSelected -> {
                MaterialTheme.colorScheme.secondary
            }

            else -> {
                MaterialTheme.colorScheme.surface
            }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        // Q3 — time + type fonts: InkSubtle when voided, InkMuted (onSurfaceVariant) when active.
        Text(
            text = bookedTimeLabel(session.bookedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = if (session.isVoided) InkSubtle else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(2f),
        ) {
            ClientNameText(session)
            if (session.isWalkIn) {
                WalkInDot(voided = session.isVoided, modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        // Session type is a creation-time snapshot; status and final price remain editable.
        Box(modifier = Modifier.weight(1f)) {
            SessionTypeBadge(session)
        }
        DashboardEditableCell(
            session = session,
            field = DashboardEditField.STATUS,
            canEdit = canEdit,
            edit = edit,
            onSessionClick = onSessionSelect,
            onEditStart = onEditStart,
            onEditDraftChange = onEditDraftChange,
            onEditCommit = onEditCommit,
            onEditDiscard = onEditDiscard,
            onEditReload = onEditReload,
            modifier = Modifier.weight(1f),
            requiresReason = requiresReason,
            onEditReasonChange = onEditReasonChange,
        )
        DashboardEditableCell(
            session = session,
            field = DashboardEditField.FINAL_PRICE,
            // #405 — a medical-mission session is always ₱0 (BR §Session types): no price
            // editor; the disabled clickable passes the tap through to row selection.
            canEdit = canEdit && !missionPriceLocked(session.sessionType),
            edit = edit,
            onSessionClick = onSessionSelect,
            onEditStart = onEditStart,
            onEditDraftChange = onEditDraftChange,
            onEditCommit = onEditCommit,
            onEditDiscard = onEditDiscard,
            onEditReload = onEditReload,
            modifier = Modifier.weight(1f),
            requiresReason = requiresReason,
            onEditReasonChange = onEditReasonChange,
        )
        // Q3 — VOIDED pill right-aligned in the dedicated 22% slot.
        Box(
            modifier = Modifier.weight(DESKTOP_VOIDED_SLOT_WEIGHT),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (session.isVoided) {
                VoidedPill()
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/**
 * #149 — a table cell for [DashboardEditField]: hover-revealed edit affordance when [canEdit]
 * (Q4 — silent when the capability is revoked), the in-place editor while this cell owns the
 * edit, and the inline error / conflict / changed-remotely line underneath (ADR-0022).
 */
@Composable
private fun DashboardEditableCell(
    session: DashboardSessionResponse,
    field: DashboardEditField,
    canEdit: Boolean,
    edit: DashboardEditState?,
    onSessionClick: (DashboardSessionResponse) -> Unit,
    onEditStart: (String, DashboardEditField) -> Unit,
    onEditDraftChange: (String) -> Unit,
    onEditCommit: () -> Unit,
    onEditDiscard: () -> Unit,
    onEditReload: () -> Unit,
    modifier: Modifier = Modifier,
    requiresReason: Boolean = false,
    onEditReasonChange: (String) -> Unit = {},
) {
    val isEditing = edit != null && edit.sessionId == session.id && edit.field == field
    Column(modifier = modifier) {
        when {
            // #403 — a REMITTED day's editor is a dialog collecting the audit reason;
            // the inline cell editors stay untouched for every other day state.
            isEditing && requiresReason -> {
                RemittedReasonDialog(
                    edit = edit,
                    actions =
                        RemittedEditActions(
                            onDraftChange = onEditDraftChange,
                            onReasonChange = onEditReasonChange,
                            onCommit = onEditCommit,
                            onDiscard = onEditDiscard,
                            onReload = onEditReload,
                        ),
                )
            }

            isEditing -> {
                EditControl(
                    edit = edit,
                    onDraftChange = onEditDraftChange,
                    onCommit = onEditCommit,
                    onDiscard = onEditDiscard,
                )
                EditStatusLine(edit = edit, onReload = onEditReload)
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
                        onSessionClick(session)
                        onEditStart(session.id, field)
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
private fun EditControl(
    edit: DashboardEditState,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
) {
    when (edit.field) {
        DashboardEditField.STATUS -> {
            SelectEditor(
                values = SessionStatus.entries.map { it.name },
                edit = edit,
                onDraftChange = onDraftChange,
                onCommit = onCommit,
                onDiscard = onDiscard,
            )
        }

        DashboardEditField.FINAL_PRICE -> {
            PriceEditor(edit, onDraftChange, onCommit, onDiscard)
        }
    }
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
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
    autoCommit: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = edit.draft,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = !edit.inFlight,
            isError = edit.error != null,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !edit.inFlight) { menuOpen = true }
                    .onPreviewKeyEvent {
                        // Menu-open Esc is consumed by the popup (dismiss); only a
                        // menu-closed Esc discards the edit.
                        if (it.key == Key.Escape && !menuOpen) {
                            onDiscard()
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
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        menuOpen = false
                        onDraftChange(value)
                        // #403 — inside the REMITTED-day dialog the Confirm button owns the
                        // commit (the reason must be collected first).
                        if (autoCommit) onCommit()
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
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
    autoCommit: Boolean = true,
) {
    OutlinedTextField(
        value = edit.draft,
        onValueChange = onDraftChange,
        singleLine = true,
        enabled = !edit.inFlight,
        isError = edit.error != null,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = { if (autoCommit) onCommit() },
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
                .onFocusChanged { if (!it.isFocused && !edit.conflict && autoCommit) onCommit() }
                .onPreviewKeyEvent {
                    if (it.key == Key.Escape) {
                        onDiscard()
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

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    header: Boolean,
) {
    Text(
        text = text,
        style =
            if (header) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier,
    )
}

private const val DESKTOP_VOIDED_SLOT_WEIGHT = 0.22f
private val EDIT_SPINNER_SIZE = 12.dp
private val EDIT_SPINNER_STROKE = 2.dp

/**
 * #403 — the edit-machine hooks the REMITTED-day dialog forwards (the *CreateParams
 * parameter-object shape; keeps the composable under the LongParameterList budget).
 */
internal data class RemittedEditActions(
    val onDraftChange: (String) -> Unit,
    val onReasonChange: (String) -> Unit,
    val onCommit: () -> Unit,
    val onDiscard: () -> Unit,
    val onReload: () -> Unit,
)
