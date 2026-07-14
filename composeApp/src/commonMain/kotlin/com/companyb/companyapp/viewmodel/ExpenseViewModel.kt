package com.companyb.companyapp.viewmodel

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
        viewModelScope.launch {
            _expenses.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/expenses?branchDayId=$branchDayId")
                if (response.status.isSuccess()) {
                    _expenses.value = UiState.Success(response.body())
                } else {
                    _expenses.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _expenses.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createExpense(request: CreateExpenseRequest) {
        viewModelScope.launch {
            _createResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/expenses") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _createResult.value = UiState.Success(response.body())
                } else {
                    _createResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _createResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteExpense(
        expenseId: String,
        request: DeleteExpenseRequest,
    ) {
        viewModelScope.launch {
            _deleteResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.delete("/api/expenses/$expenseId") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _deleteResult.value = UiState.Success(Unit)
                } else {
                    _deleteResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _deleteResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
