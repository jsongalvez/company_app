package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.dto.AddPractitionerRequest
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.SessionPractitionerResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.SessionDetailViewModel
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * SessionDetail route on both platforms (#152 — the #151 resolution).
 *
 * The dashboard path passes the enriched row via nav args — the VM seeds Success with it and
 * never dispatches (zero extra requests). The Notifications call site navigates with sessionId
 * only (row = null) — the VM fetches once via `GET /api/sessions/{sessionId}` (bearer-only
 * gate; 404 for both non-bearer and missing → the single Error + Retry fallback below).
 * Content is the same [SessionDetailContent] the desktop inline pane renders — byte-identical
 * rendering with the dashboard path.
 *
 * #348 — practitioners refresh + add-self: a side-loaded [SessionViewModel] loads the NEW
 * `GET /api/sessions/{sessionId}/practitioners` on entry and re-loads it after an add lands;
 * its slot-ordered rows override the rendered list (names kept from the enriched row where
 * present — the GET carries ids only). On a PENDING session the current user not yet among
 * the practitioners gets an "Add self" button (BR §203–206); errors — including the 403 gate —
 * surface inline.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    viewModel: SessionDetailViewModel,
    apiClient: ApiClient,
    onBack: () -> Unit,
) {
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(apiClient) }
    val detailState by viewModel.detail.collectAsState()
    val practitionersState by sessionVm.practitioners.collectAsState()
    val practitionerResult by sessionVm.practitionerResult.collectAsState()
    val currentUser by SessionState.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("SessionDetailScreen", "composable entered: sessionId=$sessionId")
        viewModel.loadIfNeeded()
        // #348 — the add-self gate needs the fresh practitioner roster even when the enriched
        // row arrived via nav args (it may predate an add made moments ago).
        sessionVm.loadSessionPractitioners(sessionId)
    }

    LaunchedEffect(detailState) {
        (detailState as? UiState.Error)?.let {
            logWarn("SessionDetailScreen", "detailState=Error: ${it.message}")
        }
    }

    LaunchedEffect(practitionerResult) {
        when (val result = practitionerResult) {
            is UiState.Success -> sessionVm.loadSessionPractitioners(sessionId)
            is UiState.Error -> logWarn("SessionDetailScreen", "practitionerResult=Error: ${result.message}")
            else -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // #113 content-level Back precedent (pushed-route topbar pattern stays fog).
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }
        when (val state = detailState) {
            // Idle is unreachable for this VM (init is Loading|Success) but keeps the when
            // exhaustive over the sealed UiState (the ClientDetailScreen pattern).
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(
                                text = state.message,
                                color = InkSubtle,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { viewModel.retry() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                val freshRows = (practitionersState as? UiState.Success)?.data
                // The GET's rows carry no display names — keep each id's name from the
                // enriched row when it has one, so only genuinely NEW practitioners fall back.
                val displaySession =
                    if (freshRows != null) {
                        val namesById = state.data.practitioners.associate { it.practitionerId to it.displayName }
                        state.data.copy(
                            practitioners =
                                freshRows.map { row ->
                                    DashboardPractitionerResponse(
                                        practitionerId = row.practitionerId,
                                        displayName = namesById[row.practitionerId] ?: FALLBACK_PRACTITIONER_NAME,
                                        remarks = row.remarks,
                                        slotAtTime = row.slotAtTime,
                                    )
                                },
                        )
                    } else {
                        state.data
                    }
                SessionDetailContent(session = displaySession)
                AddSelfSection(
                    sessionStatus = state.data.sessionStatus,
                    practitionersState = practitionersState,
                    practitionerResult = practitionerResult,
                    currentUserId = currentUser?.id,
                    onAddSelf = {
                        sessionVm.addPractitioner(
                            sessionId,
                            // Idempotency key minted at submit (BR §390–392); duplicate adds
                            // are idempotent server-side anyway.
                            AddPractitionerRequest(id = Uuid.random().toString(), practitionerId = currentUser!!.id),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AddSelfSection(
    sessionStatus: SessionStatus,
    practitionersState: UiState<List<SessionPractitionerResponse>>,
    practitionerResult: UiState<SessionPractitionerResponse>,
    currentUserId: String?,
    onAddSelf: () -> Unit,
) {
    if (sessionStatus != SessionStatus.PENDING || currentUserId == null) return
    val roster = (practitionersState as? UiState.Success)?.data ?: return
    // Not while an add/rename/remove round-trip is in flight (the same Loading guard the VM
    // enforces per call — this hides the affordance too, pass-1 edge).
    if (practitionerResult is UiState.Loading) return
    if (roster.any { it.practitionerId == currentUserId }) return

    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        TextButton(onClick = onAddSelf) {
            Text("Add self as practitioner")
        }
        (practitionerResult as? UiState.Error)?.let { error ->
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val FALLBACK_PRACTITIONER_NAME = "Practitioner"
