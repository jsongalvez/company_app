package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ApiCallHandler(
    private val scope: CoroutineScope,
    private val tag: String,
) {
    fun <T> launch(
        state: MutableStateFlow<UiState<T>>,
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        transform: suspend (HttpResponse) -> T,
        entryMessage: String = "$operation called",
    ): Job {
        logInfo(tag, entryMessage)
        return scope.launch {
            state.value = UiState.Loading
            try {
                logInfo(tag, endpoint)
                val response = block()
                if (response.status.isSuccess()) {
                    logInfo(tag, "$operation success")
                    state.value = UiState.Success(transform(response))
                } else {
                    logInfo(tag, "$operation failed: status=${response.status.value}")
                    state.value = UiState.Error("$operation failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError(tag, "$operation exception", e)
                state.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun launchUnit(
        state: MutableStateFlow<UiState<Unit>>,
        operation: String,
        endpoint: String,
        block: suspend () -> HttpResponse,
        entryMessage: String = "$operation called",
    ): Job = launch(state, operation, endpoint, block, { Unit }, entryMessage)
}
