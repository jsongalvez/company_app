package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.ConcernResponse
import com.companyb.companyapp.dto.DashboardPractitionerResponse
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.UnvoidSessionRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.dto.VoidSessionRequest
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.SessionViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Per-selection dialog targets; see the `key(session.id)` note on [SessionDetailPane]. */
internal class PaneDialogTargets {
    var removeConcern by mutableStateOf<ConcernResponse?>(null)
    var removePractitioner by mutableStateOf<DashboardPractitionerResponse?>(null)
    var remarksTarget by mutableStateOf<DashboardPractitionerResponse?>(null)
    var showAddPractitioner by mutableStateOf(false)
    var showPromoteOther by mutableStateOf(false)
    var showVoid by mutableStateOf(false)
    var showUnvoid by mutableStateOf(false)

    // #419 — the session-linked product-sale dialog slot (per-selection, like the rest).
    var showSell by mutableStateOf(false)

    // #406 — the sticky void/unvoid flows are armed with the issuing session id (#486
    // keys one VM per selection, so the armed landing always belongs to this scope; the
    // check stays as the same-scope guard): only an armed landing acts, a late landing
    // from a switched-away selection drains silently instead of repainting the wrong pane.
    var voidArmedFor by mutableStateOf<String?>(null)
    var unvoidArmedFor by mutableStateOf<String?>(null)
}

/** #406 — which void affordance an editable row offers, or none when the gate is closed. */
internal enum class SessionVoidAffordance { VOID, UNVOID }

internal fun sessionVoidAffordance(
    canVoid: Boolean,
    isVoided: Boolean,
): SessionVoidAffordance? =
    when {
        !canVoid -> null
        isVoided -> SessionVoidAffordance.UNVOID
        else -> SessionVoidAffordance.VOID
    }

/**
 * #406 — the void/unvoid client gate, mirroring the backend's
 * `requireBranchCapabilityForSession(VOID_SESSION)` exactly: a BRANCH-scoped
 * `VOID_SESSION` grant at the session's branch. Deliberately NOT the edit gate — the
 * server has no branch-day leg on these routes (a relief day grant of EDIT_BRANCH_DATA
 * does not authorize voiding), so the mirror has none either.
 */
internal fun canVoidSession(
    capabilities: List<UserCapabilityResponse>,
    branchId: String?,
): Boolean = capabilities.hasCapability(CapabilityCodes.VOID_SESSION, CapabilityContextType.BRANCH, branchId)

/** #406 — the desktop-only Void/Unvoid affordance under the detail content. */
@Composable
internal fun VoidActionSection(
    affordance: SessionVoidAffordance?,
    mutating: Boolean,
    onVoid: () -> Unit,
    onUnvoid: () -> Unit,
) {
    if (affordance == null) return
    TextButton(onClick = if (affordance == SessionVoidAffordance.VOID) onVoid else onUnvoid, enabled = !mutating) {
        val voiding = affordance == SessionVoidAffordance.VOID
        Text(
            if (voiding) "Void session…" else "Unvoid session…",
            color =
                if (voiding) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

/**
 * #406 — void/unvoid landings: any terminal result drains exactly once, but only an
 * armed landing (submitted by THIS selection — see [PaneDialogTargets]) acts. Success
 * reloads the authoritative row (VoidedPill + dimming repaint); a 409/403/4xx failure
 * unwedges via the same authoritative reload instead of wedging.
 */
@Composable
internal fun VoidUnvoidEffects(
    sessionVm: SessionViewModel,
    sessionId: String,
    results: PaneResults,
    targets: PaneDialogTargets,
    refreshSession: () -> Unit,
) {
    LaunchedEffect(results.voidResult) {
        when (val result = results.voidResult) {
            is UiState.Success -> {
                if (targets.voidArmedFor == sessionId) refreshSession()
            }

            is UiState.Error -> {
                logWarn("SessionDetailVM", "void failed: ${result.message}")
                if (targets.voidArmedFor == sessionId) refreshSession()
            }

            else -> {
                return@LaunchedEffect
            }
        }
        sessionVm.consumeVoidResult()
    }
    LaunchedEffect(results.unvoidResult) {
        when (val result = results.unvoidResult) {
            is UiState.Success -> {
                if (targets.unvoidArmedFor == sessionId) refreshSession()
            }

            is UiState.Error -> {
                logWarn("SessionDetailVM", "unvoid failed: ${result.message}")
                if (targets.unvoidArmedFor == sessionId) refreshSession()
            }

            else -> {
                return@LaunchedEffect
            }
        }
        sessionVm.consumeUnvoidResult()
    }
}

/** The pane's two reason dialogs plus their arm-on-submit wiring (#406). */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun PaneVoidDialogs(
    sessionVm: SessionViewModel,
    session: DashboardSessionResponse,
    mutating: Boolean,
    targets: PaneDialogTargets,
) {
    VoidSessionDialogHost(
        visible = targets.showVoid,
        mutating = mutating,
        onConfirmed = { request ->
            targets.voidArmedFor = session.id
            sessionVm.voidSession(session.id, request)
        },
        onDismissed = { targets.showVoid = false },
    )
    UnvoidSessionDialogHost(
        visible = targets.showUnvoid,
        mutating = mutating,
        onConfirmed = { request ->
            targets.unvoidArmedFor = session.id
            sessionVm.unvoidSession(session.id, request)
        },
        onDismissed = { targets.showUnvoid = false },
    )
}

/**
 * #406 — void the session: the required reason is recorded server-side (blank is rejected)
 * and the idempotency key is minted at submit (the #382 add-practitioner shape).
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun VoidSessionDialogHost(
    visible: Boolean,
    mutating: Boolean,
    onConfirmed: (VoidSessionRequest) -> Unit,
    onDismissed: () -> Unit,
) {
    if (!visible) return
    ReasonConfirmDialog(
        prompt =
            ReasonPrompt(
                title = "Void session?",
                caption = "The session stays on record but leaves commissions and reporting totals.",
                confirmLabel = "Void",
                destructive = true,
            ),
        inFlight = mutating,
        onConfirm = { reason ->
            onDismissed()
            onConfirmed(VoidSessionRequest(id = Uuid.random().toString(), voidReason = reason.trim()))
        },
        onDismiss = onDismissed,
    )
}

/** #406 — reverse a void; the reversal reason is likewise required. */
@Composable
private fun UnvoidSessionDialogHost(
    visible: Boolean,
    mutating: Boolean,
    onConfirmed: (UnvoidSessionRequest) -> Unit,
    onDismissed: () -> Unit,
) {
    if (!visible) return
    ReasonConfirmDialog(
        prompt =
            ReasonPrompt(
                title = "Unvoid session?",
                caption = "The session returns to commissions and reporting totals.",
                confirmLabel = "Unvoid",
                destructive = false,
            ),
        inFlight = mutating,
        onConfirm = { reason ->
            onDismissed()
            onConfirmed(UnvoidSessionRequest(unvoidedReason = reason.trim()))
        },
        onDismiss = onDismissed,
    )
}

/** Static copy for a confirm-with-required-reason dialog (#412 arity fix). */
private data class ReasonPrompt(
    val title: String,
    val caption: String,
    val confirmLabel: String,
    val destructive: Boolean,
)

/** #406 — shared confirm-with-required-reason dialog (server rejects blank reasons). */
@Composable
private fun ReasonConfirmDialog(
    prompt: ReasonPrompt,
    inFlight: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(prompt.caption, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Reason") },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = !inFlight && draft.isNotBlank()) {
                val color =
                    if (prompt.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Text(prompt.confirmLabel, color = color)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
