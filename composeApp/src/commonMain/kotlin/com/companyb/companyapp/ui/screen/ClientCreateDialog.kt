package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #348 — create-client dialog, shared by the Clients screen and the SessionCreate picker's
 * create-new path. The CreateUserDialog shape (#345): required fields gate the submit button,
 * fields disable while the POST is in flight, backend 400 policy bodies render inline via the
 * extracted error message, Success closes through the caller's [UiState] effect, and dismiss is
 * blocked while Loading. The UUID idempotency key is minted at submit time (BR §390–392) — a
 * double-tap reuses the in-flight request's guard; a retried dialog gets a fresh key.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun ClientCreateDialog(
    createState: UiState<ClientResponse>,
    onCreate: (CreateClientRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val form = remember { ClientFormState() }

    val inFlight = createState is UiState.Loading
    val age = form.ageText.toIntOrNull()
    val systolic = form.systolicText.toShortOrNull()
    val diastolic = form.diastolicText.toShortOrNull()
    // BR: blood-pressure pair supplied together — one value alone is invalid.
    val bpPairValid =
        (form.systolicText.isBlank() && form.diastolicText.isBlank()) || (systolic != null && diastolic != null)
    val complete =
        form.firstName.isNotBlank() && form.lastName.isNotBlank() && age != null && age > 0 && bpPairValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New client") },
        text = {
            Column {
                ClientIdentityFields(form, inFlight)
                Spacer(Modifier.size(Spacing.sm))
                ClientVitalsFields(form, inFlight, bpPairValid)
                (createState as? UiState.Error)?.let { state ->
                    LaunchedEffect(state) {
                        // Sticky branch — log once per state, not per recomposition.
                        logWarn("ClientCreateDialog", "createState=Error: ${state.message}")
                    }
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        CreateClientRequest(
                            id = Uuid.random().toString(),
                            firstName = form.firstName.trim(),
                            lastName = form.lastName.trim(),
                            middleName = form.middleName.trim().ifBlank { null },
                            gender = form.gender,
                            age = age ?: 0,
                            systolicBp = systolic,
                            diastolicBp = diastolic,
                        ),
                    )
                },
                enabled = complete && !inFlight,
            ) {
                Text(if (inFlight) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inFlight) {
                Text("Cancel")
            }
        },
    )
}

/** The dialog's text/chip fields; one holder so the field groups share a single param. */
private class ClientFormState {
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var middleName by mutableStateOf("")
    var gender by mutableStateOf(Gender.M)
    var ageText by mutableStateOf("")
    var systolicText by mutableStateOf("")
    var diastolicText by mutableStateOf("")
}

@Composable
private fun ClientIdentityFields(
    form: ClientFormState,
    inFlight: Boolean,
) {
    OutlinedTextField(
        value = form.firstName,
        onValueChange = { form.firstName = it },
        label = { Text("First name") },
        singleLine = true,
        enabled = !inFlight,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    OutlinedTextField(
        value = form.lastName,
        onValueChange = { form.lastName = it },
        label = { Text("Last name") },
        singleLine = true,
        enabled = !inFlight,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    OutlinedTextField(
        value = form.middleName,
        onValueChange = { form.middleName = it },
        label = { Text("Middle name (optional)") },
        singleLine = true,
        enabled = !inFlight,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = form.gender == Gender.M,
            onClick = { form.gender = Gender.M },
            label = { Text("Male") },
            enabled = !inFlight,
        )
        FilterChip(
            selected = form.gender == Gender.F,
            onClick = { form.gender = Gender.F },
            label = { Text("Female") },
            enabled = !inFlight,
        )
    }
}

@Composable
private fun ClientVitalsFields(
    form: ClientFormState,
    inFlight: Boolean,
    bpPairValid: Boolean,
) {
    OutlinedTextField(
        value = form.ageText,
        onValueChange = { form.ageText = it },
        label = { Text("Age") },
        singleLine = true,
        enabled = !inFlight,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = form.systolicText,
            onValueChange = { form.systolicText = it },
            label = { Text("Systolic BP") },
            singleLine = true,
            enabled = !inFlight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = form.diastolicText,
            onValueChange = { form.diastolicText = it },
            label = { Text("Diastolic BP") },
            singleLine = true,
            enabled = !inFlight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    if (!bpPairValid) {
        Text(
            text = "Blood pressure needs both values",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
