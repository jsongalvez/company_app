@file:OptIn(ExperimentalUuidApi::class)

package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.RateResponse
import com.companyb.companyapp.dto.SetRateRequest
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.workforce.branch.BranchViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #418 — coordinator base-rate admin (the "No base rate configured" dead end's UI half).
 * Branch-scoped to the clocked-in branch (`selectedBranchId`, the Inventory/Remittance shape);
 * the route gate lives at the NavHost call sites ([canManageRates] — exact-scope MANAGE_PRODUCTS
 * mirroring `SessionBaseRateRoutes`' filter, backend authoritative). Reuses the previously
 * orphaned [BranchViewModel.loadRates]/[BranchViewModel.setRate] legs.
 *
 * Behavior: five canonical rows in BR display order ([toRateDisplayRows]), each with an inline
 * amount field validated by [rateInputError] (the route 400s mirrored) and a Save action that
 * POSTs a fresh idempotency id — the backend rotates the previous open row and answers
 * 201/200. MEDICAL_MISSION is locked ₱0 everywhere ([missionPriceLocked]; the server
 * additionally normalizes, #405 invariant shape). Any terminal save outcome reloads the rates
 * authoritatively (pessimistic, ADR-0022); failures surface as an inline error line.
 */
@Composable
fun BaseRatesScreen(
    viewModel: BranchViewModel,
    branchId: String?,
) {
    val ratesState by viewModel.rates.collectAsState()
    val setRateState by viewModel.setRateState.collectAsState()

    LaunchedEffect(branchId) {
        if (branchId != null) viewModel.loadRates(branchId)
    }
    // Any terminal set-rate landing reloads the list authoritatively (pessimistic, ADR-0022).
    // The Idle/Loading states never trigger a load — entry loading owns that leg.
    LaunchedEffect(setRateState) {
        if ((setRateState is UiState.Success || setRateState is UiState.Error) && branchId != null) {
            viewModel.loadRates(branchId)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Base Rates", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Per-session-type prices for this branch. New sessions price from these values.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val state = ratesState) {
            is UiState.Idle, is UiState.Loading -> {
                if (branchId == null) {
                    ErrorCard(message = "No branch selected", onRetry = {})
                } else {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                ErrorCard(
                    message = state.message,
                    onRetry = { if (branchId != null) viewModel.loadRates(branchId) },
                )
            }

            is UiState.Success -> {
                RateRows(viewModel, branchId, state.data, setRateState)
            }
        }
    }
}

@Composable
private fun RateRows(
    viewModel: BranchViewModel,
    branchId: String?,
    rates: List<RateResponse>,
    setRateState: UiState<RateResponse>,
) {
    val saving = setRateState is UiState.Loading
    (setRateState as? UiState.Error)?.let { error ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(Spacing.md),
            )
        }
    }
    val rows = toRateDisplayRows(rates)
    rows.forEachIndexed { index, row ->
        RateRow(viewModel, branchId, row, saving)
        if (index < rows.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun RateRow(
    viewModel: BranchViewModel,
    branchId: String?,
    row: RateDisplayRow,
    saving: Boolean,
) {
    // Per-row draft keyed on the authoritative values so a post-save reload (fresh ids after
    // rotation) resets the fields to what the server now holds.
    var draft by rememberSaveable(row.rateId, row.rateText) { mutableStateOf(row.rateText.orEmpty()) }
    var attempted by rememberSaveable(row.rateId, row.rateText) { mutableStateOf(false) }
    val validationError = rateInputError(draft).takeIf { attempted || draft.isNotBlank() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.spec.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        when {
                            row.missionLocked -> "Always ₱0"
                            row.rateText == null -> "Default ${row.spec.defaultLabel} — not yet customized"
                            else -> "Default ${row.spec.defaultLabel}"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = if (row.missionLocked) "0" else draft,
                onValueChange = {
                    attempted = true
                    draft = it
                },
                enabled = !row.missionLocked && !saving,
                singleLine = true,
                isError = validationError != null,
                supportingText = validationError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (branchId != null) {
                        viewModel.setRate(
                            branchId,
                            SetRateRequest(
                                id = Uuid.random().toString(),
                                sessionType = row.spec.sessionType,
                                rate = draft.trim(),
                            ),
                        )
                    }
                },
                enabled = !row.missionLocked && !saving && validationError == null && draft.isNotBlank(),
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        }
    }
}
