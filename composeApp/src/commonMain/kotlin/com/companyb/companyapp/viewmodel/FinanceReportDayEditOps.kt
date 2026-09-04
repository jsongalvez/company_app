package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateAllowanceRequest
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.UpdateCompensationRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow

// #479 — extracted from FinanceReportsViewModel so the file-function wall (TMF) stays
// honest. Extension functions on the ViewModel; FinanceReportsScreen method references and
// FinanceReportsViewModelTest (same package) resolve them unchanged.

internal fun FinanceReportsViewModel.loadEditData() {
    val day = selectedDayState.value ?: return
    val branchDayId = day.branchDayId
    editDataGeneration++
    val generation = editDataGeneration
    if (hasEditBranchDataCapability()) {
        loadSection(
            generation,
            editExpensesState,
            "expenses",
            ApiRoutes.EXPENSES,
            params = listOf("branchDayId" to branchDayId),
            errorKeyPrefixes = listOf("expense:"),
        )
    }
    if (hasAssignCapability()) {
        loadSection(
            generation,
            editCompensationsState,
            "compensations",
            ApiRoutes.COMPENSATIONS,
            params = listOf("branchDayId" to branchDayId),
            errorKeyPrefixes = listOf("comp:"),
        )
        loadSection(
            generation,
            editAllowancesState,
            "allowances",
            ApiRoutes.ALLOWANCES,
            params = listOf("branchDayId" to branchDayId),
            errorKeyPrefixes = listOf("allow:"),
        )
        loadSection(
            generation,
            editUsersState,
            "branch-day users",
            ApiRoutes.branchDayUsers(branchDayId),
            params = emptyList(),
        )
    }
}

internal fun FinanceReportsViewModel.reloadSection(section: EditSection) {
    val day = selectedDayState.value ?: return
    val generation = editDataGeneration
    when (section) {
        EditSection.EXPENSES -> {
            loadSection(
                generation,
                editExpensesState,
                "expenses",
                ApiRoutes.EXPENSES,
                params = listOf("branchDayId" to day.branchDayId),
                errorKeyPrefixes = listOf("expense:"),
            )
        }

        EditSection.COMPENSATIONS -> {
            loadSection(
                generation,
                editCompensationsState,
                "compensations",
                ApiRoutes.COMPENSATIONS,
                params = listOf("branchDayId" to day.branchDayId),
                errorKeyPrefixes = listOf("comp:"),
            )
        }

        EditSection.ALLOWANCES -> {
            loadSection(
                generation,
                editAllowancesState,
                "allowances",
                ApiRoutes.ALLOWANCES,
                params = listOf("branchDayId" to day.branchDayId),
                errorKeyPrefixes = listOf("allow:"),
            )
        }
    }
}

@Suppress("LongParameterList")
internal inline fun <reified T> FinanceReportsViewModel.loadSection(
    generation: Int,
    state: MutableStateFlow<UiState<List<T>>>,
    operation: String,
    endpoint: String,
    params: List<Pair<String, String>>,
    errorKeyPrefixes: List<String> = emptyList(),
) {
    handler.launchStateless(
        operation = operation,
        endpoint = "GET $endpoint",
        block = {
            apiClient.httpClient.get(endpoint) {
                params.forEach { (k, v) -> parameter(k, v) }
            }
        },
        transform = {
            val list = it.body<List<T>>()
            state.value = UiState.Success(list)
            // #143 class — a fresh list supersedes the section's stale action
            // errors (e.g. a 409-reload landing beside its own error line).
            if (errorKeyPrefixes.isNotEmpty()) {
                actionTracker.clearWhere { key -> errorKeyPrefixes.any { key.startsWith(it) } }
            }
        },
        hooks =
            StatelessHooks(
// #173 — the generation guard folds into the stale gate (a superseded day/branch
                // section load — clearEditData during flight — must not repopulate cleared state).
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    state.value = UiState.Error("$operation failed: ${response.status.value}")
                },
                onError = { e ->
                    // Transport or deserialization failure: route to the section's error state —
                    // gated by the stale flag so a superseded day/branch load can't error the
                    // cleared sections. onError is that single surface (#169).
                    state.value = UiState.Error("$operation failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.clearEditData() {
    // P4 pass-1: bumping the generation makes in-flight section loads from a superseded
    // day/branch inert — they must not repopulate the cleared sections.
    editDataGeneration++
    editExpensesState.value = UiState.Idle
    editCompensationsState.value = UiState.Idle
    editAllowancesState.value = UiState.Idle
    editUsersState.value = UiState.Idle
    actionTracker.clear()
    conflictsState.value = emptySet()
}

// ─────────────────────────── expense actions ───────────────────────────

internal fun FinanceReportsViewModel.createCompensation(
    userId: String,
    amount: String,
    note: String?,
    reason: String?,
) {
    val day = selectedDayState.value ?: return
    val key = "comp:create"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "createCompensation",
        endpoint = "POST /api/compensation",
        block = {
            apiClient.httpClient.post(ApiRoutes.COMPENSATION) {
                setBody(
                    CreateCompensationRequest(
                        id = newId(),
                        workBranchDayId = day.branchDayId,
                        payingBranchDayId = day.branchDayId,
                        userId = userId,
                        amount = amount,
                        note = note,
                        reason = reason,
                    ),
                )
            }
        },
        transform = {
            val created = it.body<CompensationResponse>()
            val current = editCompensationsState.value
            if (current is UiState.Success) {
                editCompensationsState.value = UiState.Success(current.data + created)
            } else {
                reloadSection(EditSection.COMPENSATIONS)
            }
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    // #101 D4 — 409 duplicate (one per user per paying day) → inline error on the
                    // picker; the row is already compensated (the list shows it).
                    failActionOrSilent403(
                        key,
                        "comp:create",
                        response,
                        conflictMessage = "Already compensated on this day",
                    )
                },
                onError = { e ->
                    actionTracker.fail(key, "comp:create failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.updateCompensation(
    compensation: CompensationResponse,
    amount: String,
    note: String?,
    reason: String?,
) {
    val key = "comp:update:${compensation.id}"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "updateCompensation",
        endpoint = "PATCH /api/compensation/${compensation.id}",
        block = {
            apiClient.httpClient.patch(ApiRoutes.compensation(compensation.id)) {
                setBody(
                    UpdateCompensationRequest(
                        amount = amount,
                        expectedVersion = compensation.version,
                        note = note,
                        reason = reason,
                    ),
                )
            }
        },
        transform = {
            val updated = it.body<CompensationResponse>()
            editCompensationsState.value =
                UiState.Success(
                    (editCompensationsState.value as? UiState.Success<List<CompensationResponse>>)
                        ?.data
                        .orEmpty()
                        .map { row -> if (row.id == updated.id) updated else row },
                )
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    failActionOrSilent403(
                        key,
                        "comp:update",
                        response,
                        conflictMessage = "Compensation changed elsewhere — reloaded",
                    ) {
                        reloadSection(EditSection.COMPENSATIONS)
                    }
                },
                onError = { e ->
                    actionTracker.fail(key, "comp:update failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

// ─────────────────────────── allowance actions ───────────────────────────

internal fun FinanceReportsViewModel.createAllowance(
    userId: String,
    amount: String,
    reason: String?,
) {
    val day = selectedDayState.value ?: return
    val key = "allow:create"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "createAllowance",
        endpoint = "POST /api/allowances",
        block = {
            apiClient.httpClient.post(ApiRoutes.ALLOWANCES) {
                setBody(
                    CreateAllowanceRequest(
                        id = newId(),
                        branchDayId = day.branchDayId,
                        userId = userId,
                        amount = amount,
                        reason = reason,
                    ),
                )
            }
        },
        transform = {
            val created = it.body<AllowanceResponse>()
            val current = editAllowancesState.value
            if (current is UiState.Success) {
                editAllowancesState.value = UiState.Success(current.data + created)
            } else {
                reloadSection(EditSection.ALLOWANCES)
            }
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    failActionOrSilent403(key, "allow:create", response)
                },
                onError = { e ->
                    actionTracker.fail(key, "allow:create failed: ${e.message ?: "network error"}")
                },
            ),
    )
}
