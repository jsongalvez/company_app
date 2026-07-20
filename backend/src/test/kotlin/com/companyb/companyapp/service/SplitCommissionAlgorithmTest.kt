package com.companyb.companyapp.service

import com.companyb.companyapp.service.finance.commission.CommissionService
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitCommissionAlgorithmTest {
    @Test
    fun `single eligible user gets the full total`() {
        val result =
            CommissionService.splitCommission(
                commissionAmount = BigDecimal("150.00"),
                quantity = 3,
                eligibleUserCount = 1,
            )
        assertEquals(BigDecimal("450.0000"), result)
    }

    @Test
    fun `two eligible users split evenly`() {
        val result =
            CommissionService.splitCommission(
                commissionAmount = BigDecimal("150.00"),
                quantity = 3,
                eligibleUserCount = 2,
            )
        assertEquals(BigDecimal("225.0000"), result)
    }

    @Test
    fun `three users split one repeating cent, rounding uses HALF_UP`() {
        val result =
            CommissionService.splitCommission(
                commissionAmount = BigDecimal("1.00"),
                quantity = 1,
                eligibleUserCount = 3,
            )
        assertTrue(result.toDouble() < 0.34)
        assertTrue(result.toDouble() >= 0.33)
    }

    @Test
    fun `large quantity and many users produce consistent decimals`() {
        val result =
            CommissionService.splitCommission(
                commissionAmount = BigDecimal("250.75"),
                quantity = 10,
                eligibleUserCount = 7,
            )
        assertEquals(4, result.scale())
        assertTrue(result > BigDecimal.ZERO)
    }
}
