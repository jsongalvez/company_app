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
                    canEdit = args.canEdit,
                    edit = args.edit,
                    onEditStart = args.onEditStart,
                    onEditDraftChange = args.onEditDraftChange,
                    onEditCommit = args.onEditCommit,
                    onEditDiscard = args.onEditDiscard,
                    onEditReload = args.onEditReload,
                )
            }
        }
    }
}

@Composable
private fun TableHeaderRow(onRefresh: () -> Unit) {
    // Right-aligned action row (pass-2: the Refresh button must NOT occupy the VOIDED
    // column — the spec's 22% slot is data, not chrome).
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

@Composable
private fun DashboardTableRow(
    session: DashboardSessionResponse,
    isSelected: Boolean,
    onClick: () -> Unit,
    canEdit: Boolean,
    edit: DashboardEditState?,
    onEditStart: (String, DashboardEditField) -> Unit,
    onEditDraftChange: (String) -> Unit,
    onEditCommit: () -> Unit,
    onEditDiscard: () -> Unit,
    onEditReload: () -> Unit,
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
        // #149 — the three editable cells (type / status / final price, #97 Q4; basePrice
        // read-only). Clicking a cell selects the row too (the detail pane follows the edit).
        DashboardEditableCell(
            session = session,
            field = DashboardEditField.TYPE,
            canEdit = canEdit,
            edit = edit,
            onEditStart = onEditStart,
            onEditDraftChange = onEditDraftChange,
            onEditCommit = onEditCommit,
            onEditDiscard = onEditDiscard,
            onEditReload = onEditReload,
            modifier = Modifier.weight(1f),
        )
        DashboardEditableCell(
            session = session,
            field = DashboardEditField.STATUS,
            canEdit = canEdit,
            edit = edit,
            onEditStart = onEditStart,
            onEditDraftChange = onEditDraftChange,
            onEditCommit = onEditCommit,
            onEditDiscard = onEditDiscard,
            onEditReload = onEditReload,
            modifier = Modifier.weight(1f),
        )
        DashboardEditableCell(
            session = session,
            field = DashboardEditField.FINAL_PRICE,
            canEdit = canEdit,
            edit = edit,
            onEditStart = onEditStart,
            onEditDraftChange = onEditDraftChange,
            onEditCommit = onEditCommit,
            onEditDiscard = onEditDiscard,
            onEditReload = onEditReload,
            modifier = Modifier.weight(1f),
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
    onEditStart: (String, DashboardEditField) -> Unit,
    onEditDraftChange: (String) -> Unit,
    onEditCommit: () -> Unit,
    onEditDiscard: () -> Unit,
    onEditReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = edit != null && edit.sessionId == session.id && edit.field == field
    Column(modifier = modifier) {
        if (isEditing) {
            EditControl(
                edit = edit,
                onDraftChange = onEditDraftChange,
                onCommit = onEditCommit,
                onDiscard = onEditDiscard,
            )
        } else {
            CellDisplay(
                session = session,
                field = field,
                canEdit = canEdit,
                onEditStart = onEditStart,
            )
        }
        if (isEditing) {
            EditStatusLine(edit = edit, onReload = onEditReload)
        }
    }
}

@Composable
private fun CellDisplay(
    session: DashboardSessionResponse,
    field: DashboardEditField,
    canEdit: Boolean,
    onEditStart: (String, DashboardEditField) -> Unit,
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
                    onClick = { onEditStart(session.id, field) },
                ),
    ) {
        when (field) {
            DashboardEditField.TYPE -> {
                SessionTypeBadge(session)
            }

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
        DashboardEditField.TYPE -> {
            SelectEditor(
                values = SessionType.entries.map { it.name },
                edit = edit,
                onDraftChange = onDraftChange,
                onCommit = onCommit,
            )
        }

        DashboardEditField.STATUS -> {
            SelectEditor(
                values = SessionStatus.entries.map { it.name },
                edit = edit,
                onDraftChange = onDraftChange,
                onCommit = onCommit,
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
 */
@Composable
private fun SelectEditor(
    values: List<String>,
    edit: DashboardEditState,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
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
                    .clickable(enabled = !edit.inFlight) { menuOpen = true },
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
                        onCommit()
                    },
                )
            }
        }
    }
}

/** Q4 — price commits on Enter/blur; Esc discards (#142 editor shape + the VM's in-flight guard). */
@Composable
private fun PriceEditor(
    edit: DashboardEditState,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onDiscard: () -> Unit,
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
                onDone = { onCommit() },
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
                // Blur-commit (Q4); the VM's in-flight guard absorbs the dispose-time blur.
                .onFocusChanged { if (!it.isFocused) onCommit() }
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
private fun EditStatusLine(
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
