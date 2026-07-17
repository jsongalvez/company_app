package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.ClockInRequest
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.ClockOutRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AttendanceViewModel
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.BranchViewModel
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private sealed class Screen {
    data object Home : Screen()

    data object ClientSearch : Screen()

    data class SessionCreate(
        val clientId: String,
    ) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    branchViewModel: BranchViewModel,
    attendanceViewModel: AttendanceViewModel,
    apiClient: ApiClient,
) {
    val branchesState by branchViewModel.branches.collectAsState()
    val clockInState by attendanceViewModel.clockInState.collectAsState()
    val clockOutState by attendanceViewModel.clockOutState.collectAsState()
    val logoutState by authViewModel.logoutState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var activeAttendance by remember { mutableStateOf<ClockInResponse?>(null) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(Unit) {
        logInfo("HomeScreen", "composable entered (first composition)")
        if (branchesState is UiState.Idle) {
            branchViewModel.loadBranches()
        }
    }

    LaunchedEffect(clockInState) {
        when (val state = clockInState) {
            is UiState.Success -> {
                logInfo("HomeScreen", "clockInState=Success")
                activeAttendance = state.data
            }

            is UiState.Error -> {
                logWarn("HomeScreen", "clockInState=Error: ${state.message}")
                snackbarHostState.showSnackbar(state.message)
            }

            else -> {}
        }
    }

    LaunchedEffect(clockOutState) {
        when (val state = clockOutState) {
            is UiState.Success -> {
                logInfo("HomeScreen", "clockOutState=Success")
                activeAttendance = null
            }

            is UiState.Error -> {
                logWarn("HomeScreen", "clockOutState=Error: ${state.message}")
                snackbarHostState.showSnackbar(state.message)
            }

            else -> {}
        }
    }

    when (currentScreen) {
        is Screen.SessionCreate -> {
            val sessionViewModel = remember { SessionViewModel(apiClient) }
            val branchesList =
                when (val state = branchesState) {
                    is UiState.Success -> state.data
                    else -> emptyList()
                }
            SessionCreateScreen(
                clientId = (currentScreen as Screen.SessionCreate).clientId,
                branches = branchesList,
                sessionViewModel = sessionViewModel,
                onBack = {
                    logInfo("HomeScreen", "back from session create to client search")
                    currentScreen = Screen.ClientSearch
                },
                onSessionCreated = {
                    logInfo("HomeScreen", "session created, returning to client search")
                    currentScreen = Screen.ClientSearch
                },
            )
        }

        Screen.ClientSearch -> {
            val clientViewModel = remember { ClientViewModel(apiClient) }
            ClientSearchScreen(
                clientViewModel = clientViewModel,
                onBack = {
                    logInfo("HomeScreen", "back from client search to home")
                    currentScreen = Screen.Home
                },
                onClientSelected = { clientId ->
                    logInfo("HomeScreen", "client selected: $clientId, navigating to session create")
                    currentScreen = Screen.SessionCreate(clientId)
                },
            )
        }

        Screen.Home -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("CompanyApp") },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        actions = {
                            TextButton(
                                onClick = {
                                    logInfo("HomeScreen", "logout button onClick")
                                    authViewModel.logout()
                                },
                                enabled = logoutState !is UiState.Loading,
                            ) {
                                Text("Logout")
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                when (val state = branchesState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(onClick = { branchViewModel.loadBranches() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        HomeScreenContent(
                            branches = state.data,
                            activeAttendance = activeAttendance,
                            isClockingIn = clockInState is UiState.Loading,
                            isClockingOut = clockOutState is UiState.Loading,
                            onClockIn = { branchId ->
                                attendanceViewModel.clockIn(
                                    ClockInRequest(
                                        attendanceId = Uuid.random().toString(),
                                        branchId = branchId,
                                    ),
                                )
                            },
                            onClockOut = { attendanceId ->
                                attendanceViewModel.clockOut(ClockOutRequest(attendanceId))
                            },
                            onClientsClick = {
                                logInfo("HomeScreen", "clients button onClick")
                                currentScreen = Screen.ClientSearch
                            },
                            modifier = Modifier.padding(padding),
                        )
                    }

                    is UiState.Idle -> {}
                }
            }
        }
    }
}

@Composable
private fun HomeScreenContent(
    branches: List<BranchResponse>,
    activeAttendance: ClockInResponse?,
    isClockingIn: Boolean,
    isClockingOut: Boolean,
    onClockIn: (branchId: String) -> Unit,
    onClockOut: (attendanceId: String) -> Unit,
    onClientsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            QuickNavRow(onClientsClick = onClientsClick)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Branches",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        items(branches) { branch ->
            BranchCard(
                branch = branch,
                activeAttendance = activeAttendance,
                isClockingIn = isClockingIn,
                isClockingOut = isClockingOut,
                onClockIn = { onClockIn(branch.id) },
                onClockOut = {
                    activeAttendance?.let { onClockOut(it.id) }
                },
            )
        }
    }
}

@Composable
private fun QuickNavRow(onClientsClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onClientsClick,
            modifier = Modifier.weight(1f),
        ) {
            Text("Clients")
        }
        FilledTonalButton(
            onClick = { /* TODO: navigate to sessions */ },
            modifier = Modifier.weight(1f),
        ) {
            Text("Sessions")
        }
        FilledTonalButton(
            onClick = { /* TODO: navigate to inventory */ },
            modifier = Modifier.weight(1f),
        ) {
            Text("Inventory")
        }
    }
}

@Composable
private fun BranchCard(
    branch: BranchResponse,
    activeAttendance: ClockInResponse?,
    isClockingIn: Boolean,
    isClockingOut: Boolean,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
) {
    val isClockedInHere = activeAttendance?.let { it.branchDayId.startsWith(branch.id.take(36)) } ?: false
    val isClockedInElsewhere = activeAttendance != null && !isClockedInHere

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isClockedInHere) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = branch.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BranchTypeBadge(branch.branchType)
                if (isClockedInHere) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clocked in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isClockedInElsewhere) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Clocked in elsewhere",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            when {
                isClockedInHere -> {
                    Button(
                        onClick = onClockOut,
                        enabled = !isClockingOut,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        if (isClockingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Clock Out")
                        }
                    }
                }

                isClockedInElsewhere -> {
                    Button(
                        onClick = onClockIn,
                        enabled = false,
                    ) {
                        Text("Clock In")
                    }
                }

                else -> {
                    Button(
                        onClick = onClockIn,
                        enabled = !isClockingIn,
                    ) {
                        if (isClockingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Clock In")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchTypeBadge(branchType: BranchType) {
    val label =
        when (branchType) {
            BranchType.CLINIC -> "Clinic"
            BranchType.PROVINCIAL_TOUR -> "Provincial Tour"
            BranchType.MEDICAL_MISSION -> "Medical Mission"
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
