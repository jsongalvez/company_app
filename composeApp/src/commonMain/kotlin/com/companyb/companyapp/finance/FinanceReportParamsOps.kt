package com.companyb.companyapp.finance

import androidx.lifecycle.ViewModel
import com.companyb.companyapp.async.UiState
import io.ktor.client.request.parameter

// #479 — the report-parameter seam (mode/month/range/jump), extracted from
// FinanceReportsViewModel so the file-function wall (TMF) stays honest. Extension functions
// on the ViewModel; same-package consumers resolve them without imports (#559).

internal fun FinanceReportsViewModel.setMode(mode: ReportMode) {
    if (mode == modeState.value) return
    modeState.value = mode
    paramErrorState.value = null
    refreshWindowAndFeed()
    if (mode == ReportMode.MONTHLY) {
        loadMonthlyRollup()
    } else {
        monthlyRollupState.value = UiState.Idle
    }
}

internal fun FinanceReportsViewModel.setMonthInput(raw: String) {
    monthInputState.value = raw
}

internal fun FinanceReportsViewModel.applyMonth() {
    val month =
        parseYearMonthInput(monthInputState.value)
    if (month == null) {
        paramErrorState.value = "Month must be yyyy-MM"
        return
    }
    paramErrorState.value = null
    appliedMonthState.value = month
    monthlyRollupState.value = UiState.Idle
    refreshWindowAndFeed()
    loadMonthlyRollup()
}

internal fun FinanceReportsViewModel.clearRange() {
    appliedRangeState.value = null
    paramErrorState.value = null
    refreshWindowAndFeed()
}

internal fun FinanceReportsViewModel.setRangeInputs(
    from: String,
    to: String,
) {
    rangeFromInputState.value = from
    rangeToInputState.value = to
}

internal fun FinanceReportsViewModel.applyRange() {
    val from =
        parseDateInput(rangeFromInputState.value)
    val to =
        parseDateInput(rangeToInputState.value)
    when {
        from == null -> {
            paramErrorState.value = "From must be yyyy-MM-dd"
        }

        to == null -> {
            paramErrorState.value = "To must be yyyy-MM-dd"
        }

        from > to -> {
            paramErrorState.value = "From must be on or before To"
        }

        else -> {
            paramErrorState.value = null
            appliedRangeState.value = from.toString() to to.toString()
            refreshWindowAndFeed()
        }
    }
}

internal fun FinanceReportsViewModel.setJumpInput(raw: String) {
    monthInputState.value = raw
}

internal fun FinanceReportsViewModel.applyJump() {
    val month =
        parseYearMonthInput(monthInputState.value)
    if (month == null) {
        paramErrorState.value = "Month must be yyyy-MM"
        return
    }
    paramErrorState.value = null
    jumpMonthState.value = month
    refreshWindowAndFeed()
}

internal fun FinanceReportsViewModel.clearJump() {
    jumpMonthState.value = null
    monthInputState.value = defaultMonth.toString()
    paramErrorState.value = null
    refreshWindowAndFeed()
}

// ─────────────────────────── feed ───────────────────────────
