package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuditLogViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _entries = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val entries: StateFlow<UiState<List<AuditLogEntryResponse>>> = _entries.asStateFlow()

    private val _flaggedEntries = MutableStateFlow<UiState<List<AuditLogEntryResponse>>>(UiState.Idle)
    val flaggedEntries: StateFlow<UiState<List<AuditLogEntryResponse>>> = _flaggedEntries.asStateFlow()

    private val _acknowledgeResult = MutableStateFlow<UiState<AuditLogEntryResponse>>(UiState.Idle)
    val acknowledgeResult: StateFlow<UiState<AuditLogEntryResponse>> = _acknowledgeResult.asStateFlow()

    fun loadEntries(
        tableName: String,
        recordId: String,
    ) {
        viewModelScope.launch {
            _entries.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.get(
                        "/api/audit-log?tableName=$tableName&recordId=$recordId",
                    )
                if (response.status.isSuccess()) {
                    _entries.value = UiState.Success(response.body())
                } else {
                    _entries.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _entries.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadFlaggedEntries() {
        viewModelScope.launch {
            _flaggedEntries.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/audit-log/flagged")
                if (response.status.isSuccess()) {
                    _flaggedEntries.value = UiState.Success(response.body())
                } else {
                    _flaggedEntries.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _flaggedEntries.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun acknowledgeEntry(entryId: String) {
        viewModelScope.launch {
            _acknowledgeResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch(
                        "/api/audit-log/$entryId/acknowledge",
                    )
                if (response.status.isSuccess()) {
                    _acknowledgeResult.value = UiState.Success(response.body())
                } else {
                    _acknowledgeResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _acknowledgeResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
