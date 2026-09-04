package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.SessionResponse
import com.companyb.companyapp.dto.UpdateSessionFinalPriceRequest
import com.companyb.companyapp.dto.UpdateSessionStatusRequest
import com.companyb.companyapp.ui.screen.DashboardEditField
import com.companyb.companyapp.ui.screen.DashboardEditState
import com.companyb.companyapp.ui.screen.draftChanged
import com.companyb.companyapp.ui.screen.finalPriceInputValid
import com.companyb.companyapp.ui.screen.normalizedReason
import com.companyb.companyapp.ui.screen.remittedReasonRequired
import com.companyb.companyapp.ui.screen.statusEditAllowed
import com.companyb.companyapp.ui.screen.statusOptionsFor

// #479 — the dashboard edit-policy support seam, extracted from SessionDashboardViewModel so
// the file-function wall (TMF) stays honest: per-field gating, the #135 client-side 400
// mirrors, commit-row merge, and the request build. Extension functions on the ViewModel;
// consumed by the edit machine (SessionDashboardEditOps) via same-package visibility.

private const val INVALID_STATUS_MESSAGE = "Choose a legal status transition"
private const val INVALID_PRICE_MESSAGE = "Enter a valid amount (digits only, e.g. 2500.00)"
private const val DAY_STATE_UNAVAILABLE_MESSAGE = "Day state unavailable — refresh before retrying"
private const val REASON_REQUIRED_MESSAGE = "A reason is required to write on a REMITTED day"

internal fun SessionDashboardViewModel.fieldEditAllowed(
    row: DashboardSessionResponse,
    field: DashboardEditField,
): Boolean =
    dayEditAllowed() &&
        (
            field != DashboardEditField.STATUS ||
                statusEditAllowed(
                    isWalkIn = row.isWalkIn,
                    currentStatus = row.sessionStatus,
                    hasCorrectionAuthority = canCorrectStatusState.value,
                    dayStatus = dayStatusState.value,
                )
        )

internal fun SessionDashboardViewModel.canReplaceEdit(current: DashboardEditState?): Boolean =
    current?.inFlight != true &&
        (
            current?.error == null ||
                // Keep a failed draft until it is discarded, unless its row vanished from the list.
                lastDataCache.value?.sessions?.none { it.id == current.sessionId } == true
        )

internal fun SessionDashboardViewModel.commitRow(updated: SessionResponse) {
    val data = lastDataCache.value ?: return
    val rows =
        data.sessions.map { row ->
            if (row.id == updated.id) {
                mergeCommittedRow(row, updated)
            } else {
                row
            }
        }
    lastDataCache.value = data.copy(sessions = rows)
}

internal fun SessionDashboardViewModel.mergeCommittedRow(
    row: DashboardSessionResponse,
    updated: SessionResponse,
): DashboardSessionResponse =
    row.copy(
        sessionType = updated.sessionType,
        isWalkIn = updated.isWalkIn,
        sessionStatus = updated.sessionStatus,
        basePrice = updated.basePrice,
        finalPrice = updated.finalPrice,
        remarks = updated.remarks,
        otherConcerns = updated.otherConcerns,
        bookedAt = updated.bookedAt,
        nextAppointmentDate = updated.nextAppointmentDate,
        version = updated.version,
        // dashboard-only fields (clientName / isVoided / practitioners / concerns)
        // keep the existing row's — the PATCH response carries none of them.
    )

internal data class EditRequest(
    val path: String,
    val body: Any,
)

/**
 * The client-side mirrors of the backend 400s (#135): an invalid price draft or a
 * missing REMITTED-day reason fail the edit before any request is sent.
 */
internal fun SessionDashboardViewModel.draftValidationFailure(
    state: DashboardEditState,
    row: DashboardSessionResponse,
): String? =
    when {
        state.field == DashboardEditField.STATUS && !dayEditAllowed() -> DAY_STATE_UNAVAILABLE_MESSAGE

        state.field == DashboardEditField.STATUS &&
            state.draft !in
            statusOptionsFor(
                isWalkIn = row.isWalkIn,
                currentStatus = baselineStatus(state, row),
                hasCorrectionAuthority = canCorrectStatusState.value,
                dayStatus = dayStatusState.value,
            ) -> INVALID_STATUS_MESSAGE

        state.field == DashboardEditField.FINAL_PRICE && !dayEditAllowed() -> DAY_STATE_UNAVAILABLE_MESSAGE

        state.field == DashboardEditField.FINAL_PRICE && !finalPriceInputValid(state.draft) -> INVALID_PRICE_MESSAGE

        remittedReasonRequired(dayStatusState.value) && state.reason.isBlank() -> REASON_REQUIRED_MESSAGE

        else -> null
    }

internal fun SessionDashboardViewModel.baselineStatus(
    state: DashboardEditState,
    row: DashboardSessionResponse,
): SessionStatus =
    runCatching { SessionStatus.valueOf(state.baselineValue) }
        .getOrElse { row.sessionStatus }

internal fun SessionDashboardViewModel.editRequest(state: DashboardEditState): EditRequest {
    val reason = normalizedReason(state.reason).ifEmpty { null }
    return when (state.field) {
        DashboardEditField.STATUS -> {
            EditRequest(
                ApiRoutes.sessionStatus(state.sessionId),
                UpdateSessionStatusRequest(
                    com.companyb.companyapp.domain.SessionStatus
                        .valueOf(state.draft),
                    state.baselineVersion,
                    reason,
                ),
            )
        }

        DashboardEditField.FINAL_PRICE -> {
            EditRequest(
                ApiRoutes.sessionFinalPrice(state.sessionId),
                UpdateSessionFinalPriceRequest(state.draft.trim(), state.baselineVersion, reason),
            )
        }
    }
}
