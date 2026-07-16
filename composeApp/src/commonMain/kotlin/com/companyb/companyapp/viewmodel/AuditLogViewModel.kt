package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AuditLogEntryResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuditLogViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AuditLogVM")

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
        handler.launch(
            state = _entries,
            operation = "loadEntries",
            endpoint = "GET /api/audit-log?tableName=$tableName&recordId=$recordId",
            block = {
                apiClient.httpClient.get(
                    "/api/audit-log?tableName=$tableName&recordId=$recordId",
                )
            },
            transform = { it.body() },
        )
    }

    fun loadFlaggedEntries() {
        handler.launch(
            state = _flaggedEntries,
            operation = "loadFlaggedEntries",
            endpoint = "GET /api/audit-log/flagged",
            block = { apiClient.httpClient.get("/api/audit-log/flagged") },
            transform = { it.body() },
        )
    }

    fun acknowledgeEntry(entryId: String) {
        handler.launch(
            state = _acknowledgeResult,
            operation = "acknowledgeEntry",
            endpoint = "PATCH /api/audit-log/$entryId/acknowledge",
            block = { apiClient.httpClient.patch("/api/audit-log/$entryId/acknowledge") },
            transform = { it.body() },
        )
    }
}
