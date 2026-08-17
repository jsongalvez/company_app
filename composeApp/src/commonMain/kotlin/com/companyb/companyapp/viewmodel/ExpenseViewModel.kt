package com.companyb.companyapp.viewmodel
import com.companyb.companyapp.api.ApiRoutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExpenseViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ExpenseVM")

    private val _expenses = MutableStateFlow<UiState<List<ExpenseResponse>>>(UiState.Idle)
    val expenses: StateFlow<UiState<List<ExpenseResponse>>> = _expenses.asStateFlow()

    private val _createResult = MutableStateFlow<UiState<ExpenseResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<ExpenseResponse>> = _createResult.asStateFlow()

    private val _deleteResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteResult: StateFlow<UiState<Unit>> = _deleteResult.asStateFlow()

    fun loadExpenses(branchDayId: String) {
        handler.launch(
            state = _expenses,
            operation = "loadExpenses",
            endpoint = "GET /api/expenses?branchDayId=$branchDayId",
            block = { apiClient.httpClient.get("/api/expenses?branchDayId=$branchDayId") },
            transform = { it.body() },
        )
    }

    fun createExpense(request: CreateExpenseRequest) {
        handler.launch(
            state = _createResult,
            operation = "createExpense",
            endpoint = "POST /api/expenses",
            block = {
                apiClient.httpClient.post(ApiRoutes.EXPENSES) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun deleteExpense(
        expenseId: String,
        request: DeleteExpenseRequest,
    ) {
        handler.launchUnit(
            state = _deleteResult,
            operation = "deleteExpense",
            endpoint = "DELETE /api/expenses/$expenseId",
            block = {
                apiClient.httpClient.delete("/api/expenses/$expenseId") {
                    setBody(request)
                }
            },
        )
    }
}
