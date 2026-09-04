package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.RestoreExpenseRequest
import com.companyb.companyapp.dto.UpdateExpenseRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

// #479 — extracted from FinanceReportsViewModel so the file-function wall (TMF) stays
// honest. Extension functions on the ViewModel; FinanceReportsScreen method references and
// FinanceReportsViewModelTest (same package) resolve them unchanged.

internal fun FinanceReportsViewModel.createExpense(
    amount: String,
    categoryCode: String,
    notes: String?,
    reason: String?,
) {
    val day = selectedDayState.value ?: return
    val key = "expense:create"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "createExpense",
        endpoint = "POST /api/expenses",
        block = {
            apiClient.httpClient.post(ApiRoutes.EXPENSES) {
                setBody(
                    CreateExpenseRequest(
                        id = newId(),
                        branchDayId = day.branchDayId,
                        amount = amount,
                        category =
                            com.companyb.companyapp.domain.ExpenseCategory
                                .valueOf(categoryCode),
                        notes = notes,
                        reason = reason,
                    ),
                )
            }
        },
        transform = {
            val created = it.body<ExpenseResponse>()
            val current = editExpensesState.value
            if (current is UiState.Success) {
                editExpensesState.value = UiState.Success(current.data + created)
            } else {
                // Pass-8 SOFT — appending onto an Error section would truncate the
                // list to the new row; reload the section instead.
                reloadSection(EditSection.EXPENSES)
            }
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
// #173 — a superseded action (branch/day switched mid-flight) is inert: no row
                // write onto the new day's sections, no tracker terminal (clearEditData cleared it).
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    failActionOrSilent403(key, "expense:create", response)
                },
                onError = { e ->
                    // Pass-9 HARD — a transport/timeout failure must keep the dialog open with an
                    // inline error (the close-on-success effect keys on the ABSENCE of an error).
                    actionTracker.fail(key, "expense:create failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.updateExpense(
    expense: ExpenseResponse,
    amount: String,
    categoryCode: String,
    notes: String?,
    reason: String?,
) {
    val key = "expense:update:${expense.id}"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "updateExpense",
        endpoint = "PATCH /api/expenses/${expense.id}",
        block = {
            apiClient.httpClient.patch(ApiRoutes.expense(expense.id)) {
                setBody(
                    UpdateExpenseRequest(
                        amount = amount,
                        category =
                            com.companyb.companyapp.domain.ExpenseCategory
                                .valueOf(categoryCode),
                        notes = notes,
                        expectedVersion = expense.version,
                        reason = reason,
                    ),
                )
            }
        },
        transform = {
            val updated = it.body<ExpenseResponse>()
            replaceExpenseRow(updated)
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
// #173 — the superseded-PATCH gate folds into the stale flag (a branch/day switch
                // mid-flight must leave the action + its tracker terminal inert).
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    failActionOrSilent403(
                        key,
                        "expense:update",
                        response,
                        conflictMessage = "Expense changed elsewhere — reloaded",
                    ) {
                        reloadSection(EditSection.EXPENSES)
                    }
                },
                onError = { e ->
                    actionTracker.fail(key, "expense:update failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.deleteExpense(
    expense: ExpenseResponse,
    reason: String,
) {
    val key = "expense:delete:${expense.id}"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "deleteExpense",
        endpoint = "DELETE /api/expenses/${expense.id}",
        block = {
            apiClient.httpClient.delete(ApiRoutes.expense(expense.id)) {
                setBody(DeleteExpenseRequest(reason = reason))
            }
        },
        transform = {
            val deleted = it.body<ExpenseResponse>()
            replaceExpenseRow(deleted)
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    failActionOrSilent403(key, "expense:delete", response)
                },
                onError = { e ->
                    actionTracker.fail(key, "expense:delete failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.restoreExpense(
    expense: ExpenseResponse,
    reason: String?,
) {
    val key = "expense:restore:${expense.id}"
    if (!actionTracker.tryBegin(key)) return
    val generation = editDataGeneration
    handler.launchStateless(
        operation = "restoreExpense",
        endpoint = "POST /api/expenses/${expense.id}/restore",
        block = {
            apiClient.httpClient.post(ApiRoutes.expenseRestore(expense.id)) {
                setBody(RestoreExpenseRequest(reason = reason))
            }
        },
        transform = {
            val restored = it.body<ExpenseResponse>()
            replaceExpenseRow(restored)
            actionTracker.finish(key)
        },
        hooks =
            StatelessHooks(
                stale = { generation != editDataGeneration },
                onNonSuccess = { response ->
                    failActionOrSilent403(key, "expense:restore", response)
                },
                onError = { e ->
                    // Pass-10 HARD — the one onError the pass-9 batch missed: a transport failure
                    // must keep the restore dialog open with an inline error (the close-on-success
                    // effect keys on the ABSENCE of an error).
                    actionTracker.fail(key, "expense:restore failed: ${e.message ?: "network error"}")
                },
            ),
    )
}

internal fun FinanceReportsViewModel.replaceExpenseRow(updated: ExpenseResponse) {
    val current =
        (editExpensesState.value as? UiState.Success<List<ExpenseResponse>>)
            ?.data
            .orEmpty()
    editExpensesState.value =
        UiState.Success(current.map { if (it.id == updated.id) updated else it })
}

// ─────────────────────────── compensation actions ───────────────────────────

/**
 * ADR-0022 / #113 D4 — 403s exit silently (the capability surface is code-only; the backend
 * is authoritative and a silent exit must not flash an error the user can't act on).
 * 409 = conflict → message + section reload; other statuses → generic inline error.
 */
internal fun FinanceReportsViewModel.failActionOrSilent403(
    key: String,
    operation: String,
    response: HttpResponse,
    conflictMessage: String? = null,
    reload: (() -> Unit)? = null,
) {
    if (response.status == HttpStatusCode.Forbidden) {
        actionTracker.finish(key)
        return
    }
    val message =
        when {
            response.status == HttpStatusCode.Conflict && conflictMessage != null -> conflictMessage
            else -> "$operation failed: ${response.status.value}"
        }
    actionTracker.fail(key, message)
    if (response.status == HttpStatusCode.Conflict) {
        conflictsState.value = conflictsState.value + key
        reload?.invoke()
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
internal fun FinanceReportsViewModel.newId(): String =
    kotlin.uuid.Uuid
        .random()
        .toString()
