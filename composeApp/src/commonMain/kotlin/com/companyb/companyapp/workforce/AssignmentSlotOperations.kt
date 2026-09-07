package com.companyb.companyapp.workforce

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.contracts.workforce.SwapSlotsRequest
import com.companyb.companyapp.contracts.workforce.UpdateSlotRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

/**
 * #457 — one assignment/slot-operation seam for branch + attendance flows (profile/admin
 * reuse the same shape): the PATCH slot / POST swap HTTP construction lives here so the
 * four ViewModels share endpoint + body instead of duplicating them. UiState handling
 * stays in each caller (branch admin vs roster refresh vs self-service differ).
 */
internal object AssignmentSlotOperations {
    suspend fun updateSlot(
        apiClient: ApiClient,
        branchId: String,
        assignmentId: String,
        slot: Short,
    ): HttpResponse =
        apiClient.httpClient.patch(ApiRoutes.branchAssignmentSlot(branchId, assignmentId)) {
            setBody(UpdateSlotRequest(slot))
        }

    suspend fun swapSlots(
        apiClient: ApiClient,
        branchId: String,
        assignmentIdA: String,
        assignmentIdB: String,
    ): HttpResponse =
        apiClient.httpClient.post(ApiRoutes.branchSlotsSwap(branchId)) {
            setBody(SwapSlotsRequest(assignmentIdA, assignmentIdB))
        }
}
