package com.companyb.companyapp.client

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.ui.contract.FieldErrorText
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #673 — deliberate section fields: display text when the section is not editing,
 * an [OutlinedTextField] when it is. Tab/blur/Enter only move focus, never send a
 * write — only the section Save dispatches (one request via [buildSectionPatch]).
 * Missing values read as an em dash.
 */
@Composable
internal fun SectionTextField(
    label: String,
    value: String,
    draft: String?,
    error: String?,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onDraftChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft != null) {
            var fieldModifier: Modifier = Modifier.operationalField()
            if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                isError = error != null,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(),
                modifier = fieldModifier,
            )
        } else {
            Text(
                text = value.ifBlank { CLIENT_MISSING_VALUE },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (value.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) FieldErrorText(error)
    }
}

@Composable
internal fun SectionGenderField(
    value: String,
    draft: String?,
    error: String?,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    onDraftChange: (String) -> Unit,
) {
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
        if (draft != null) {
            var fieldModifier: Modifier = Modifier.operationalField()
            if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
            GenderSectionDropdown(
                draft = draft,
                enabled = enabled,
                modifier = fieldModifier,
                onDraftChange = onDraftChange,
            )
        } else {
            Text(
                text = value.ifBlank { CLIENT_MISSING_VALUE },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) FieldErrorText(error)
    }
}

@Composable
private fun GenderSectionDropdown(
    draft: String,
    enabled: Boolean,
    modifier: Modifier,
    onDraftChange: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedTextField(
            value = if (draft == Gender.M.name) Gender.M.displayName() else Gender.F.displayName(),
            onValueChange = {},
            singleLine = true,
            readOnly = true,
            modifier = modifier,
            enabled = enabled,
        )
        // Read-only field opens the menu; selecting only updates the section draft —
        // no commit fires here (the section Save owns the single write).
        TertiaryActionButton(
            label = "Change gender",
            onClick = { if (enabled) menuOpen = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
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
                    onDraftChange(Gender.M.name)
                },
            )
            DropdownMenuItem(
                text = { Text(Gender.F.displayName()) },
                enabled = enabled,
                onClick = {
                    menuOpen = false
                    onDraftChange(Gender.F.name)
                },
            )
        }
    }
}

@Composable
internal fun SectionBpPair(
    systolic: Short?,
    diastolic: Short?,
    sysDraft: String?,
    diaDraft: String?,
    error: String?,
    enabled: Boolean,
    sysFocus: FocusRequester? = null,
    onSysChange: (String) -> Unit,
    onDiaChange: (String) -> Unit,
) {
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
        if (sysDraft != null && diaDraft != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var sysModifier: Modifier = Modifier.weight(1f).operationalField()
                if (sysFocus != null) sysModifier = sysModifier.focusRequester(sysFocus)
                OutlinedTextField(
                    value = sysDraft,
                    onValueChange = onSysChange,
                    singleLine = true,
                    isError = error != null,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(),
                    modifier = sysModifier,
                )
                Text(
                    text = "/",
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = diaDraft,
                    onValueChange = onDiaChange,
                    singleLine = true,
                    isError = error != null,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(),
                    modifier = Modifier.weight(1f).operationalField(),
                )
            }
        } else {
            val display =
                when {
                    systolic != null && diastolic != null -> "$systolic / $diastolic"
                    systolic != null -> "$systolic / $CLIENT_MISSING_VALUE"
                    diastolic != null -> "$CLIENT_MISSING_VALUE / $diastolic"
                    else -> CLIENT_MISSING_VALUE
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) FieldErrorText(error)
    }
}
