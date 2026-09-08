package com.companyb.companyapp.session.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.theme.Spacing

/**
 * #674 — the persistent intake action bar: Cancel + Start session (Walk-in) or
 * Create booked session (Booked) with a reserved validation slot. It sits outside
 * the scrolling form and above the software keyboard, so Start stays reachable at
 * 1366x768 and 390x844 with the keyboard visible.
 *
 * The primary stays clickable while field input is invalid so the first invalid
 * field can receive focus with its inline explanation (the screen's attemptSubmit
 * owns that); it disables only while the preview isn't ready or a submission is
 * in flight — duplicate submits stay structurally disabled with stable geometry
 * via the contract buttons (#670).
 *
 * After a partial concern failure the bar switches to Retry failed concerns +
 * Continue to session; the retry posts only the missing links and never creates
 * another session (VM-owned).
 */
@Composable
@Suppress("LongParameterList") // #674 declarative-UI bar signature stays whole per #535.
internal fun SessionCreateActionBar(
    viewModel: SessionCreateFormApi,
    draft: SessionCreateDraft,
    hasClient: Boolean,
    showValidation: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onContinueAfterConcernFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview by viewModel.preview.collectAsState()
    val createResult by viewModel.createResult.collectAsState()
    val concernAddFailures by viewModel.concernAddFailures.collectAsState()
    val concernRetryState by viewModel.concernRetryState.collectAsState()
    val locked = isSessionCreateLocked(createResult)

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (concernAddFailures > 0 && createResult is UiState.Success) {
            ConcernFailureBar(
                failureCount = concernAddFailures,
                retryState = concernRetryState,
                onRetry = viewModel::retryConcernAdds,
                onContinue = onContinueAfterConcernFailure,
            )
        } else {
            SubmitBar(
                draft = draft,
                hasClient = hasClient,
                previewReady = preview is UiState.Success,
                createResult = createResult,
                concernRetryError = (concernRetryState as? UiState.Error)?.message,
                showValidation = showValidation,
                locked = locked,
                onCancel = onCancel,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList") // #674 declarative-UI bar row stays whole per #535.
private fun SubmitBar(
    draft: SessionCreateDraft,
    hasClient: Boolean,
    previewReady: Boolean,
    createResult: UiState<*>,
    concernRetryError: String?,
    showValidation: Boolean,
    locked: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val priceInvalid = hasClient && previewReady && parseSessionPrice(draft.finalPrice) == null
    val bookingInvalid =
        hasClient && previewReady && bookingFields(draft.isBooked, draft.nextAppointmentDate) == null
    val createError = (createResult as? UiState.Error)?.message
    // Reserved validation slot: always mounted so the bar never shifts geometry.
    // With no client the primary is disabled and the slot names the next step.
    val statusText =
        createError ?: concernRetryError
            ?: if (!hasClient) {
                "Select a client to start a session."
            } else if (showValidation && (priceInvalid || bookingInvalid)) {
                "Check the highlighted fields above."
            } else {
                ""
            }
    val statusIsError = createError != null || concernRetryError != null
    val showInvalidHint = showValidation && (priceInvalid || bookingInvalid)
    val busy = createResult is UiState.Loading
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryActionButton(
            label = "Cancel",
            onClick = onCancel,
            enabled = !locked,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (statusIsError || showInvalidHint) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
        PrimaryActionButton(
            label =
                if (busy) {
                    if (draft.isBooked) "Creating…" else "Starting…"
                } else if (draft.isBooked) {
                    "Create booked session"
                } else {
                    "Start session"
                },
            onClick = onSubmit,
            enabled = hasClient && previewReady && !locked,
            isBusy = busy,
        )
    }
}

@Composable
private fun ConcernFailureBar(
    failureCount: Int,
    retryState: UiState<Unit>,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val retryBusy = retryState is UiState.Loading
    val retryEnabled = !retryBusy
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "Session created, but $failureCount concern(s) could not be recorded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        (retryState as? UiState.Error)?.let { state ->
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryActionButton(
                label = "Retry concerns",
                onClick = onRetry,
                enabled = retryEnabled,
                isBusy = retryBusy,
            )
            PrimaryActionButton(
                label = "Continue to session",
                onClick = onContinue,
                enabled = retryEnabled,
            )
        }
    }
}
