package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.contracts.session.DashboardCommissionResponse
import com.companyb.companyapp.contracts.session.DashboardResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #672 — the summary line: session count + gross + labeled personal commission in
 * one quiet line; an unparseable commission reads "Unavailable", never a
 * fabricated zero (#654 safe parsing kept, display fallback improved).
 */
class DashboardSummaryPolicyTest {
    private fun session(
        id: String,
        status: SessionStatus = SessionStatus.COMPLETED,
        price: String = "2500.00",
        voided: Boolean = false,
    ) = DashboardSessionResponse(
        id = id,
        clientId = "c1",
        clientName = "Client $id",
        sessionType = SessionType.REGULAR,
        isWalkIn = false,
        sessionStatus = status,
        basePrice = "2500.00",
        finalPrice = price,
        remarks = null,
        otherConcerns = null,
        bookedAt = null,
        nextAppointmentDate = null,
        version = 1,
        isVoided = voided,
    )

    private fun dashboard(
        sessions: List<DashboardSessionResponse>,
        commission: String = "200.0000",
        productSalesCount: Int = 1,
    ) = DashboardResponse(
        sessions = sessions,
        commission = DashboardCommissionResponse(amount = commission, productSalesCount = productSalesCount),
    )

    @Test
    fun summary_line_names_count_gross_and_labeled_commission() {
        val summary = summarizeDashboard(dashboard(listOf(session("s1"), session("s2"))))

        assertEquals(2, summary.sessionCount)
        assertEquals(500_000L, summary.grossCents)
        assertEquals(20_000L, summary.commissionCents)
        assertTrue(summary.lineText().contains("2 sessions"))
        assertTrue(summary.lineText().contains("Your commission"))
    }

    @Test
    fun gross_counts_only_completed_non_voided() {
        val summary =
            summarizeDashboard(
                dashboard(
                    listOf(
                        session("s1", SessionStatus.COMPLETED),
                        session("s2", SessionStatus.PENDING),
                        session("s3", SessionStatus.COMPLETED, voided = true),
                    ),
                ),
            )

        assertEquals(3, summary.sessionCount, "the count covers the day; the money covers completions")
        assertEquals(250_000L, summary.grossCents)
    }

    @Test
    fun unparseable_commission_is_unavailable_never_zero() {
        val summary = summarizeDashboard(dashboard(listOf(session("s1")), commission = "not-a-number"))

        assertNull(summary.commissionCents)
        assertEquals("Unavailable", summary.lineText().substringAfter("Your commission ").substringBefore(" "))
        assertTrue(!summary.lineText().contains("₱0.00"))
    }

    @Test
    fun unparseable_session_price_makes_gross_unavailable_never_zero() {
        val summary =
            summarizeDashboard(
                dashboard(
                    listOf(
                        session("s1", price = "2500.00"),
                        session("s2", price = "not-a-number"),
                    ),
                ),
            )

        assertNull(summary.grossCents)
        assertTrue(summary.lineText().contains("Gross Unavailable"))
        assertTrue(!summary.lineText().contains("₱0.00"))
    }

    @Test
    fun empty_day_still_summarizes() {
        val summary = summarizeDashboard(dashboard(emptyList(), commission = "0.0000", productSalesCount = 0))

        assertEquals(0, summary.sessionCount)
        assertEquals(0L, summary.grossCents)
        assertTrue(summary.lineText().contains("0 sessions"))
    }
}
