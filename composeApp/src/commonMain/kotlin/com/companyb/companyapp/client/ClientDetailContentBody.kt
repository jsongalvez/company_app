package com.companyb.companyapp.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.theme.Spacing

@Composable
internal fun ClientDetailContentBody(
    client: ClientResponse,
    edit: ClientFieldEditState,
    draft: BpDraftState,
    anonymizeState: UiState<Unit>,
    callbacks: ClientDetailCallbacks,
) {
    ClientDetailContentHeader(client)

    // Scroll container wraps the layout split — both actuals stay scrollable as a unit.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        ClientDetailLayout(
            identity = {
                ClientDetailNameFields(
                    client = client,
                    edit = edit,
                    callbacks = callbacks,
                )
                ClientDetailDemographicFields(
                    client = client,
                    edit = edit,
                    callbacks = callbacks,
                )
            },
            contactHealth = {
                ClientDetailContactHealth(
                    client = client,
                    edit = edit,
                    draft = draft,
                    callbacks = callbacks,
                )
            },
            actions = {
                ClientDetailActions(
                    anonymizeState = anonymizeState,
                    navigationLocked = edit.navigationLocked,
                    onAnonymizeClick = callbacks.onAnonymizeClick,
                )
            },
        )
    }
}

@Composable
private fun ClientDetailContentHeader(client: ClientResponse) {
    Text(
        text = clientDisplayName(client),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.size(Spacing.xs))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
internal fun ClientDetailNameFields(
    client: ClientResponse,
    edit: ClientFieldEditState,
    callbacks: ClientDetailCallbacks,
) {
    Spacer(Modifier.size(Spacing.sm))
    SectionLabel("Identity")
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "First name",
                value = client.firstName.orEmpty(),
                field = ClientField.FIRST_NAME,
            ),
        edit = edit,
        callbacks = callbacks,
    )
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Middle name",
                value = client.middleName.orEmpty(),
                field = ClientField.MIDDLE_NAME,
            ),
        edit = edit,
        callbacks = callbacks,
    )
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Last name",
                value = client.lastName.orEmpty(),
                field = ClientField.LAST_NAME,
            ),
        edit = edit,
        callbacks = callbacks,
    )
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Suffix",
                value = client.suffix.orEmpty(),
                field = ClientField.SUFFIX,
            ),
        edit = edit,
        callbacks = callbacks,
    )
}

@Composable
internal fun ClientDetailDemographicFields(
    client: ClientResponse,
    edit: ClientFieldEditState,
    callbacks: ClientDetailCallbacks,
) {
    GenderFieldEditor(
        value = client.gender.displayName(),
        field = ClientField.GENDER,
        edit = edit,
        callbacks = callbacks,
    )
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Age",
                value = client.age.toString(),
                field = ClientField.AGE,
                keyboardType = KeyboardType.Number,
            ),
        edit = edit,
        callbacks = callbacks,
    )
}

@Composable
internal fun ClientDetailContactHealth(
    client: ClientResponse,
    edit: ClientFieldEditState,
    draft: BpDraftState,
    callbacks: ClientDetailCallbacks,
) {
    Spacer(Modifier.size(Spacing.sm))
    SectionLabel("Contact + Health")
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Phone",
                value = client.phoneNumber.orEmpty(),
                field = ClientField.PHONE,
                keyboardType = KeyboardType.Phone,
            ),
        edit = edit,
        callbacks = callbacks,
    )
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Address",
                value = client.address.orEmpty(),
                field = ClientField.ADDRESS,
            ),
        edit = edit,
        callbacks = callbacks,
    )
    // D4 BP pair rule — both fields or neither (backend 400s otherwise): the pair
    // is one editor, committed together, so a null-BP client can gain BP values.
    BpPairEditor(
        state =
            BpPairState(
                systolic = client.systolicBp,
                diastolic = client.diastolicBp,
                editing = edit.editingField == ClientField.BP_PAIR,
                fieldError = if (edit.editingField == ClientField.BP_PAIR) edit.fieldError else null,
                draft = draft,
                enabled = !edit.navigationLocked,
            ),
        callbacks = callbacks,
    )
    ClientFieldEditor(
        spec =
            ClientFieldSpec(
                label = "Medical conditions",
                value = client.medicalConditions.orEmpty(),
                field = ClientField.MEDICAL_CONDITIONS,
            ),
        edit = edit,
        callbacks = callbacks,
    )
}

@Composable
private fun ClientDetailActions(
    anonymizeState: UiState<Unit>,
    navigationLocked: Boolean,
    onAnonymizeClick: () -> Unit,
) {
    // D5 — destructive styling (error/onError tokens per the #96 badge precedent).
    // Disabled while a POST is in flight — re-tapping mid-anonymize would fire a
    // second destructive request (a redundant 404 after the first 204).
    OutlinedButton(
        onClick = onAnonymizeClick,
        enabled = !navigationLocked && anonymizeState !is UiState.Loading,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error,
            ),
    ) {
        Text("Anonymize")
    }
    if (anonymizeState is UiState.Error) {
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = anonymizeState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
