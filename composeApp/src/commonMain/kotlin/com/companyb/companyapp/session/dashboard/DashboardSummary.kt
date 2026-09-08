package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.ui.screen.centsToMoney
import com.companyb.companyapp.ui.screen.commissionLabel
import com.companyb.companyapp.ui.screen.grossIncomeCents
import com.companyb.companyapp.ui.screen.moneyToCents
import com.companyb.companyapp.util.logWarn

/**
 * #672 — the Sessions workspace summary line: one quiet line carrying session count,
 * gross, and the labeled personal commission (replaces the equal-weight stat cards).
 *
 * No new financial calculations: gross reuses [grossIncomeCents] (completed,
 * non-voided); commission reuses the dashboard payload amount. An
 * unreadable/unavailable amount renders as "Unavailable" — never a fabricated zero
 * (#654 safe parsing kept, display fallback improved). Gross goes Unavailable when
 * any contributing row's price is unparseable (symmetric with commission); an empty
 * day still reads ₱0.00.
 *
 * Pure + composition-free (the #670 contract shape): formatting decisions are
 * unit-testable without a Compose runtime.
 */
internal data class DashboardSummary(
    val sessionCount: Int,
    val grossCents: Long?,
    val commissionCents: Long?,
    val productSalesCount: Int,
)

internal fun summarizeDashboard(data: DashboardResponse): DashboardSummary {
    val commissionCents =
        moneyToCents(data.commission.amount) ?: run {
            logWarn("DashboardSummary", "unparseable commission amount=${data.commission.amount}")
            null
        }
    val contributing =
        data.sessions.filter { it.sessionStatus == SessionStatus.COMPLETED && !it.isVoided }
    val grossCents =
        if (contributing.all { moneyToCents(it.finalPrice) != null }) {
            grossIncomeCents(data.sessions)
        } else {
            logWarn("DashboardSummary", "unparseable session price — gross unavailable")
            null
        }
    return DashboardSummary(
        sessionCount = data.sessions.size,
        grossCents = grossCents,
        commissionCents = commissionCents,
        productSalesCount = data.commission.productSalesCount,
    )
}

internal fun DashboardSummary.lineText(): String {
    val gross =
        if (grossCents == null) {
            "Gross Unavailable"
        } else {
            "Gross ₱${centsToMoney(grossCents)}"
        }
    val commission =
        if (commissionCents == null) {
            "Your commission Unavailable"
        } else {
            "Your commission ₱${centsToMoney(commissionCents)} (${commissionLabel(productSalesCount)})"
        }
    return "$sessionCount sessions · $gross · $commission"
}
