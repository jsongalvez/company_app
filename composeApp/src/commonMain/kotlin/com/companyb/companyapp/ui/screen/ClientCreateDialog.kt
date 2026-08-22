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
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.CreateClientRequest
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.UiState
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
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.M) }
    var ageText by remember { mutableStateOf("") }
    var systolicText by remember { mutableStateOf("") }
    var diastolicText by remember { mutableStateOf("") }

    val inFlight = createState is UiState.Loading
    val age = ageText.toIntOrNull()
    val systolic = systolicText.toShortOrNull()
    val diastolic = diastolicText.toShortOrNull()
    // BR: blood-pressure pair supplied together — one value alone is invalid.
    val bpPairValid =
        (systolicText.isBlank() && diastolicText.isBlank()) || (systolic != null && diastolic != null)
    val complete =
        firstName.isNotBlank() && lastName.isNotBlank() && age != null && age > 0 && bpPairValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New client") },
        text = {
            Column {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First name") },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last name") },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = middleName,
                    onValueChange = { middleName = it },
                    label = { Text("Middle name (optional)") },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = gender == Gender.M,
                        onClick = { gender = Gender.M },
                        label = { Text("Male") },
                        enabled = !inFlight,
                    )
                    FilterChip(
                        selected = gender == Gender.F,
                        onClick = { gender = Gender.F },
                        label = { Text("Female") },
                        enabled = !inFlight,
                    )
                }
                Spacer(Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text("Age") },
                    singleLine = true,
                    enabled = !inFlight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = systolicText,
                        onValueChange = { systolicText = it },
                        label = { Text("Systolic BP") },
                        singleLine = true,
                        enabled = !inFlight,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = diastolicText,
                        onValueChange = { diastolicText = it },
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
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            middleName = middleName.trim().ifBlank { null },
                            gender = gender,
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
