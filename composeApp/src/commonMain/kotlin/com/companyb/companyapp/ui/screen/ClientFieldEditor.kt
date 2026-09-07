package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.ui.theme.Spacing

/**
 * D4 — inline per-field editor: pencil affordance → edit in place → commit on Enter/blur.
 * Pessimistic: the display value only changes via PATCH success (the VM state); while editing,
 * the draft is the source of truth. Esc cancels the edit. Reads the field half of [callbacks].
 */
@Composable
internal fun ClientFieldEditor(
    spec: ClientFieldSpec,
    edit: ClientFieldEditState,
    callbacks: ClientDetailCallbacks,
) {
    val editing = edit.editingField == spec.field
    val error = if (editing) edit.fieldError else null
    val enabled = !edit.navigationLocked

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (editing) {
                OutlinedTextField(
                    value = edit.draftValue,
                    onValueChange = callbacks.onDraftChange,
                    singleLine = true,
                    isError = error != null,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = spec.keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { callbacks.onCommit(spec.field) }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .onFocusChanged { if (!it.isFocused) callbacks.onCommit(spec.field) }
                            .escapeCancels(callbacks.onCancel),
                )
            } else {
                Text(
                    text = spec.value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (spec.value.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
                // Pencil affordance — Canvas-drawn (no material-icons dependency, #107 precedent).
                PencilIcon(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .clickable(enabled = enabled) { callbacks.onStartEdit(spec.field) }
                            .padding(start = Spacing.xs),
                )
            }
        }
        ClientFieldErrorText(error)
    }
}

@Composable
private fun ClientFieldErrorText(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** D4 — gender edits via dropdown; commit fires on select ("commit on select"). */
@Composable
internal fun GenderFieldEditor(
    value: String,
    field: ClientField,
    edit: ClientFieldEditState,
    callbacks: ClientDetailCallbacks,
) {
    val editing = edit.editingField == field
    val error = if (editing) edit.fieldError else null
    val enabled = !edit.navigationLocked

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = "Gender",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            GenderEditDropdown(
                edit = edit,
                field = field,
                callbacks = callbacks,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                PencilIcon(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .clickable(enabled = enabled) { callbacks.onStartEdit(field) }
                            .padding(start = Spacing.xs),
                )
            }
        }
        // D4 inline error — the dropdown has no blur/Enter surface, so a failed PATCH must
        // surface here or it is invisible (re-selecting the same value exits the edit).
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GenderEditDropdown(
    edit: ClientFieldEditState,
    field: ClientField,
    callbacks: ClientDetailCallbacks,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val enabled = !edit.navigationLocked
    Box {
        OutlinedTextField(
            value = if (edit.draftValue == Gender.M.name) Gender.M.displayName() else Gender.F.displayName(),
            onValueChange = {},
            singleLine = true,
            readOnly = true,
            isError = edit.fieldError != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { menuOpen = true },
            enabled = enabled,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(Gender.M.displayName()) },
                enabled = enabled,
                onClick = {
                    menuOpen = false
                    callbacks.onDraftChange(Gender.M.name)
                    callbacks.onCommit(field)
                },
            )
            DropdownMenuItem(
                text = { Text(Gender.F.displayName()) },
                enabled = enabled,
                onClick = {
                    menuOpen = false
                    callbacks.onDraftChange(Gender.F.name)
                    callbacks.onCommit(field)
                },
            )
        }
    }
}

@Composable
internal fun BpPairEditor(
    state: BpPairState,
    callbacks: ClientDetailCallbacks,
) {
    // Re-seed drafts each time edit mode is entered. No value-change key needed: any reload
    // (409/404 reload, entry re-fetch) writes UiState.Loading into the detail flow, which
    // unmounts this whole editor (ClientDetailScreen renders ClientDetailContent only on
    // Success) — the drafts die with it, and re-entry seeds fresh. The only client-value change
    // that keeps the editor mounted is the user's own PATCH success (transform commits the
    // detail before updateState flips), and there the drafts already equal the committed values.
    LaunchedEffect(state.editing) {
        if (state.editing) {
            state.draft.seed(state.systolic, state.diastolic)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = "Blood pressure",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.editing) {
            BpPairEditRow(
                state = state,
                callbacks = callbacks,
            )
        } else {
            BpPairDisplayRow(
                systolic = state.systolic,
                diastolic = state.diastolic,
                enabled = state.enabled,
                onStartEdit = { callbacks.onStartEdit(ClientField.BP_PAIR) },
            )
        }
        if (state.fieldError != null) {
            Text(
                text = state.fieldError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BpPairEditRow(
    state: BpPairState,
    callbacks: ClientDetailCallbacks,
) {
    // Blur-commit lives on the ROW, not the fields: isFocused on the row is false only
    // when the whole pair lost focus (moving systolic↔diastolic keeps a descendant
    // focused — a per-field handler would blur-commit mid-correction the moment the user
    // taps back into the other side after typing both). Fires once per pair-focus-loss,
    // disposal included. Esc cancels the edit.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .onFocusChanged {
                    if (!it.isFocused && state.draft.dirtySystolic && state.draft.dirtyDiastolic) {
                        callbacks.onCommitBp()
                    }
                }.escapeCancels(callbacks.onCancel),
    ) {
        OutlinedTextField(
            value = state.draft.systolic,
            onValueChange = {
                state.draft.systolic = it
                state.draft.dirtySystolic = true
                callbacks.onBpDraftChanged()
            },
            singleLine = true,
            isError = state.fieldError != null,
            enabled = state.enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { callbacks.onCommitBp() }),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "/",
            modifier = Modifier.padding(horizontal = Spacing.xs),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.draft.diastolic,
            onValueChange = {
                state.draft.diastolic = it
                state.draft.dirtyDiastolic = true
                callbacks.onBpDraftChanged()
            },
            singleLine = true,
            isError = state.fieldError != null,
            enabled = state.enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { callbacks.onCommitBp() }),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BpPairDisplayRow(
    systolic: Short?,
    diastolic: Short?,
    enabled: Boolean,
    onStartEdit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val display =
            when {
                systolic != null && diastolic != null -> "$systolic / $diastolic"
                systolic != null -> "$systolic / —"
                diastolic != null -> "— / $diastolic"
                else -> "—"
            }
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (systolic == null && diastolic == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
        PencilIcon(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(enabled = enabled, onClick = onStartEdit)
                    .padding(start = Spacing.xs),
        )
    }
}

// Pencil glyph drawn via Canvas — the project has no material-icons dependency (only the #107
// hamburger precedent exists); a compact 45°-rotated body + tip triangle reads as a pencil.
@Suppress("MagicNumber") // geometry literals for the pencil proportions — one-time drawing constants
@Composable
private fun PencilIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .size(14.dp)
                .rotate(-45f),
    ) {
        val w = size.width
        val h = size.height
        val bodyH = h * 0.30f
        val bodyTop = (h - bodyH) / 2f
        val tipLen = w * 0.25f
        val eraserLen = w * 0.15f

        // eraser
        drawRect(
            color = tint,
            topLeft = Offset(0f, bodyTop),
            size = Size(eraserLen, bodyH),
        )
        // body
        drawRect(
            color = tint,
            topLeft = Offset(eraserLen, bodyTop),
            size = Size(w - eraserLen - tipLen, bodyH),
        )
        // tip (triangle pointing right)
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

/** Esc cancels an in-progress edit — shared by the single-field and BP-pair editors. */
private fun Modifier.escapeCancels(onCancel: () -> Unit): Modifier =
    onPreviewKeyEvent {
        if (it.key == Key.Escape) {
            onCancel()
            true
        } else {
            false
        }
    }
