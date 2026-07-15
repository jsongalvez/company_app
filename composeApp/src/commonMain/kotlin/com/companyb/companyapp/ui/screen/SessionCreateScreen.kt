package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.CreateSessionRequest
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun SessionCreateScreen(
    clientId: String,
    branches: List<BranchResponse>,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit,
    onSessionCreated: () -> Unit,
) {
    val sessionResult by sessionViewModel.sessionResult.collectAsState()
    val allConcerns by sessionViewModel.concerns.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedBranchId by remember { mutableStateOf("") }
    var isWalkIn by remember { mutableStateOf(false) }
    var finalPrice by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var nextAppointmentDate by remember { mutableStateOf("") }
    var selectedConcernIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        logInfo("SessionCreateScreen", "composable entered: clientId=$clientId")
        if (allConcerns is UiState.Idle) {
            sessionViewModel.loadAllConcerns()
        }
    }

    LaunchedEffect(sessionResult) {
        when (val state = sessionResult) {
            is UiState.Success -> {
                logInfo("SessionCreateScreen", "session created successfully")
                onSessionCreated()
            }

            is UiState.Error -> {
                logInfo("SessionCreateScreen", "session create error: ${state.message}")
                snackbarHostState.showSnackbar(state.message)
            }

            else -> {}
        }
    }

    val isSubmitting = sessionResult is UiState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Session") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                navigationIcon = {
                    TextButton(onClick = {
                        logInfo("SessionCreateScreen", "back button onClick")
                        onBack()
                    }) {
                        Text("Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BranchDropdown(
                    branches = branches,
                    selectedBranchId = selectedBranchId,
                    onBranchSelected = { selectedBranchId = it },
                    enabled = !isSubmitting,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Walk-in", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isWalkIn,
                        onCheckedChange = { isWalkIn = it },
                        enabled = !isSubmitting,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = finalPrice,
                    onValueChange = { finalPrice = it },
                    label = { Text("Final Price") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }

            item {
                OutlinedTextField(
                    value = nextAppointmentDate,
                    onValueChange = { nextAppointmentDate = it },
                    label = { Text("Next Appointment (YYYY-MM-DD)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    text = "Concerns",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            when (val concernsState = allConcerns) {
                is UiState.Loading -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = concernsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is UiState.Success -> {
                    val concerns = concernsState.data
                    if (concerns.isEmpty()) {
                        item {
                            Text(
                                text = "No concerns available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(concerns) { concern ->
                            ConcernCheckboxRow(
                                concern = concern,
                                isChecked = concern.id in selectedConcernIds,
                                enabled = !isSubmitting,
                                onToggle = { checked ->
                                    selectedConcernIds =
                                        if (checked) {
                                            selectedConcernIds + concern.id
                                        } else {
                                            selectedConcernIds - concern.id
                                        }
                                },
                            )
                        }
                    }
                }

                is UiState.Idle -> {}
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        logInfo("SessionCreateScreen", "submit button onClick")
                        if (selectedBranchId.isBlank()) {
                            // Handled via branch dropdown validation
                        }
                        sessionViewModel.createSession(
                            CreateSessionRequest(
                                id = Uuid.random().toString(),
                                clientId = clientId,
                                branchId = selectedBranchId,
                                isWalkIn = isWalkIn,
                                finalPrice = finalPrice,
                                remarks = remarks.ifBlank { null },
                                nextAppointmentDate = nextAppointmentDate.ifBlank { null },
                            ),
                        )
                    },
                    enabled =
                        !isSubmitting && selectedBranchId.isNotBlank() &&
                            finalPrice.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Create Session")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchDropdown(
    branches: List<BranchResponse>,
    selectedBranchId: String,
    onBranchSelected: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = branches.find { it.id == selectedBranchId }?.name ?: "Select a branch"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) expanded = !expanded
        },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Branch") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch.name) },
                    onClick = {
                        onBranchSelected(branch.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConcernCheckboxRow(
    concern: ConcernResponse,
    isChecked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = concern.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
