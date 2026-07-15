package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CreateExpenseRequest
import com.companyb.companyapp.dto.DeleteExpenseRequest
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpenseViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _expenses = MutableStateFlow<UiState<List<ExpenseResponse>>>(UiState.Idle)
    val expenses: StateFlow<UiState<List<ExpenseResponse>>> = _expenses.asStateFlow()

    private val _createResult = MutableStateFlow<UiState<ExpenseResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<ExpenseResponse>> = _createResult.asStateFlow()

    private val _deleteResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteResult: StateFlow<UiState<Unit>> = _deleteResult.asStateFlow()

    fun loadExpenses(branchDayId: String) {
        logInfo("ExpenseVM", "loadExpenses called")
        viewModelScope.launch {
            _expenses.value = UiState.Loading
            try {
                logInfo("ExpenseVM", "GET /api/expenses?branchDayId=$branchDayId")
                val response = apiClient.httpClient.get("/api/expenses?branchDayId=$branchDayId")
                if (response.status.isSuccess()) {
                    logInfo("ExpenseVM", "loadExpenses success")
                    _expenses.value = UiState.Success(response.body())
                } else {
                    logInfo("ExpenseVM", "loadExpenses failed: status=${response.status.value}")
                    _expenses.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ExpenseVM", "loadExpenses exception", e)
                _expenses.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createExpense(request: CreateExpenseRequest) {
        logInfo("ExpenseVM", "createExpense called")
        viewModelScope.launch {
            _createResult.value = UiState.Loading
            try {
                logInfo("ExpenseVM", "POST /api/expenses")
                val response =
                    apiClient.httpClient.post("/api/expenses") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ExpenseVM", "createExpense success")
                    _createResult.value = UiState.Success(response.body())
                } else {
                    logInfo("ExpenseVM", "createExpense failed: status=${response.status.value}")
                    _createResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ExpenseVM", "createExpense exception", e)
                _createResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteExpense(
        expenseId: String,
        request: DeleteExpenseRequest,
    ) {
        logInfo("ExpenseVM", "deleteExpense called")
        viewModelScope.launch {
            _deleteResult.value = UiState.Loading
            try {
                logInfo("ExpenseVM", "DELETE /api/expenses/$expenseId")
                val response =
                    apiClient.httpClient.delete("/api/expenses/$expenseId") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("ExpenseVM", "deleteExpense success")
                    _deleteResult.value = UiState.Success(Unit)
                } else {
                    logInfo("ExpenseVM", "deleteExpense failed: status=${response.status.value}")
                    _deleteResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ExpenseVM", "deleteExpense exception", e)
                _deleteResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
