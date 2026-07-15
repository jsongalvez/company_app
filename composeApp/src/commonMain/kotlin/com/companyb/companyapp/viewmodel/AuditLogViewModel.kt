package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
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
        logInfo("AuditLogVM", "loadEntries called")
        viewModelScope.launch {
            _entries.value = UiState.Loading
            try {
                logInfo("AuditLogVM", "GET /api/audit-log?tableName=$tableName&recordId=$recordId")
                val response =
                    apiClient.httpClient.get(
                        "/api/audit-log?tableName=$tableName&recordId=$recordId",
                    )
                if (response.status.isSuccess()) {
                    logInfo("AuditLogVM", "loadEntries success")
                    _entries.value = UiState.Success(response.body())
                } else {
                    logInfo("AuditLogVM", "loadEntries failed: status=${response.status.value}")
                    _entries.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("AuditLogVM", "loadEntries exception", e)
                _entries.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadFlaggedEntries() {
        logInfo("AuditLogVM", "loadFlaggedEntries called")
        viewModelScope.launch {
            _flaggedEntries.value = UiState.Loading
            try {
                logInfo("AuditLogVM", "GET /api/audit-log/flagged")
                val response = apiClient.httpClient.get("/api/audit-log/flagged")
                if (response.status.isSuccess()) {
                    logInfo("AuditLogVM", "loadFlaggedEntries success")
                    _flaggedEntries.value = UiState.Success(response.body())
                } else {
                    logInfo("AuditLogVM", "loadFlaggedEntries failed: status=${response.status.value}")
                    _flaggedEntries.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("AuditLogVM", "loadFlaggedEntries exception", e)
                _flaggedEntries.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun acknowledgeEntry(entryId: String) {
        logInfo("AuditLogVM", "acknowledgeEntry called")
        viewModelScope.launch {
            _acknowledgeResult.value = UiState.Loading
            try {
                logInfo("AuditLogVM", "PATCH /api/audit-log/$entryId/acknowledge")
                val response =
                    apiClient.httpClient.patch(
                        "/api/audit-log/$entryId/acknowledge",
                    )
                if (response.status.isSuccess()) {
                    logInfo("AuditLogVM", "acknowledgeEntry success")
                    _acknowledgeResult.value = UiState.Success(response.body())
                } else {
                    logInfo("AuditLogVM", "acknowledgeEntry failed: status=${response.status.value}")
                    _acknowledgeResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("AuditLogVM", "acknowledgeEntry exception", e)
                _acknowledgeResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
