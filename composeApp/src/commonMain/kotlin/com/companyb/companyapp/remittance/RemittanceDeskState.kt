package com.companyb.companyapp.remittance

import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.remittance.RemittanceResponse

/**
 * #677 — shared desk bundle built by the success host: the queue mirrors + shared list
 * state, rail callbacks, and the desk selection context (branch + selected id).
 */
internal data class RemittanceDeskState(
    val branchId: String,
    val currentId: String,
    val mirrors: Map<String, List<RemittanceResponse>>,
    val queueState: UiState<List<RemittanceResponse>>,
    val onQueueClick: (String) -> Unit,
    val onRetryQueue: () -> Unit,
)
