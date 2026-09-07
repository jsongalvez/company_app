package com.companyb.companyapp.viewmodel

import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasBranchOrDayCapability
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.async.GuardedStateless
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.isStatusCorrection
import com.companyb.companyapp.dto.BranchDayTodayResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.ui.screen.DashboardEditField
import com.companyb.companyapp.ui.screen.DashboardEditState
import com.companyb.companyapp.util.logWarn
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// #479 — the dashboard capability + day-status seam (#403 era), extracted from
// SessionDashboardViewModel so the file-function wall (TMF) stays honest: the capability
// observer (combine over SessionState), local-revocation tracking, the day-status read with
// its generation gate, and the fail-closed affordance sweeps. Extension functions on the
// ViewModel; SessionDashboardViewModelTest shares the package and resolves them unchanged,
// and the poll/edit seams call these via same-package visibility.

private data class CapabilityUpdate(
    val contextChanged: Boolean,
    val wasCanEdit: Boolean,
    val canEdit: Boolean,
    val canCorrectStatus: Boolean,
)

internal fun SessionDashboardViewModel.observeCapabilities() {
    if (capabilityJob?.isActive == true) return
    capabilityJob =
        viewModelScope.launch {
            // #498 — one coherent snapshot per emission (no multi-flow combine/reconstruction).
            AppSessionState.snapshot.collect { snap ->
                val capabilities = snap.capabilities
                val branchId = snap.clock?.branchId
                val dayId = snap.clock?.branchDayId
                val baseCanEdit =
                    capabilities.hasBranchOrDayCapability(
                        code = CapabilityCodes.EDIT_BRANCH_DATA,
                        branchId = branchId,
                        dayId = dayId,
                    )
                val baseCanCorrectStatus =
                    capabilities.hasCapability(
                        CapabilityCodes.EDIT_PAST_DAY,
                        CapabilityContextType.BRANCH,
                        branchId,
                    )
                val context = branchId to dayId
                val update =
                    updateCapabilities(
                        capabilities = capabilities,
                        baseCanEdit = baseCanEdit,
                        baseCanCorrectStatus = baseCanCorrectStatus,
                        context = context,
                    )
                applyCapabilityUpdate(update)
            }
        }
}

private fun SessionDashboardViewModel.updateCapabilities(
    capabilities: List<UserCapabilityResponse>,
    baseCanEdit: Boolean,
    baseCanCorrectStatus: Boolean,
    context: Pair<String?, String?>,
): CapabilityUpdate {
    val wasCanEdit = canEditState.value
    val contextChanged = capabilityContext != context
    val branchChanged = capabilityContext.first != context.first
    val capabilitiesChanged = capabilitySnapshot != capabilities
    resetLocalRevocations(contextChanged, branchChanged, capabilitiesChanged)
    capabilitySnapshot = capabilities
    val canEdit = baseCanEdit && locallyRevokedEditContext != context
    val canCorrectStatus = baseCanCorrectStatus && locallyRevokedCorrectionBranch != context.first
    if (contextChanged || capabilitiesChanged || wasCanEdit != canEdit) {
        dayGeneration++
    }
    capabilityContext = context
    canEditState.value = canEdit
    canCorrectStatusState.value = canCorrectStatus
    return CapabilityUpdate(contextChanged, wasCanEdit, canEdit, canCorrectStatus)
}

private fun SessionDashboardViewModel.resetLocalRevocations(
    contextChanged: Boolean,
    branchChanged: Boolean,
    capabilitiesChanged: Boolean,
) {
    if (contextChanged || capabilitiesChanged) {
        locallyRevokedEditContext = null
    }
    if (branchChanged || capabilitiesChanged) {
        locallyRevokedCorrectionBranch = null
    }
}

private fun SessionDashboardViewModel.applyCapabilityUpdate(update: CapabilityUpdate) {
    when {
        !update.canEdit -> {
            dayStatusState.value = null
            clearEdit()
        }

        update.contextChanged || !update.wasCanEdit -> {
            dayStatusState.value = null
            clearEdit()
            loadDayStatus()
        }

        dayStatusState.value != null && dayStatusState.value != DayStatus.OPEN && !update.canCorrectStatus -> {
            clearEdit()
        }

        !update.canCorrectStatus && isCorrectionEdit() -> {
            clearEdit()
        }
    }
}

internal fun SessionDashboardViewModel.isCorrectionEdit(): Boolean {
    val state = currentEditState.value ?: return false
    if (state.field != DashboardEditField.STATUS) return false
    val row = lastDataCache.value?.sessions?.firstOrNull { it.id == state.sessionId } ?: return false
    val target = runCatching { SessionStatus.valueOf(state.draft) }.getOrNull() ?: return false
    return isStatusCorrection(baselineStatus(state, row), target)
}

/**
 * #403 — the day-status read behind the reason-required gate. Fired with each dashboard
 * refresh; a failure clears status so the desktop affordance fails closed until a fresh
 * state lands.
 */
internal fun SessionDashboardViewModel.loadDayStatus() {
    val branchId =
        AppSessionState.snapshot.value.clock
            ?.branchId ?: return
    if (!canEditState.value) return
    dayStatusState.value = null
    if (dayStatusBranchId != branchId) {
        dayStatusBranchId = branchId
        clearEdit()
    }
    ++dayGeneration
    val generation = dayGeneration
    handler.launchStatelessGuarded(
        operation = "loadDayStatus",
        endpoint = "GET /api/branches/$branchId/today",
        block = { apiClient.httpClient.get(ApiRoutes.branchToday(branchId)) },
        guarded =
            GuardedStateless(
                // #528 — decode/commit split: a context switch mid-decode drops the body instead
                // of writing the old branch's status onto the new surface.
                decode = { response -> response.body<BranchDayTodayResponse>().status },
                commit = { status ->
                    dayStatusState.value = status
                    if (status != DayStatus.OPEN && !canCorrectStatusState.value) {
                        clearEdit()
                    }
                },
                onNonSuccess = { response ->
                    // 401 is the global auth path (ApiClient.onUnauthorized); 403 = grant revoked.
                    if (response.status.value == 401 || response.status.value == 403) {
                        dayStatusState.value = null
                        locallyRevokedEditContext = capabilityContext
                        locallyRevokedCorrectionBranch = capabilityContext.first
                        canEditState.value = false
                        canCorrectStatusState.value = false
                        clearEdit()
                    } else {
                        dayStatusState.value = null
                        logWarn("DashboardVM", "day-status read failed (${response.status.value}) — edit disabled")
                    }
                },
                onError = {
                    dayStatusState.value = null
                },
                stale = { generation != dayGeneration },
            ),
    )
}

internal fun SessionDashboardViewModel.dayEditAllowed(): Boolean =
    dayStatusState.value?.let { it == DayStatus.OPEN || canCorrectStatusState.value } == true
