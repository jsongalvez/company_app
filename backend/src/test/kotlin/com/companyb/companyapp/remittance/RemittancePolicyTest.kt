package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure remittance state/financial policy (#320) — the rules a database never needs to be
 * involved in: arithmetic, snapshot eligibility, DRAFT/SUBMITTED transition guards, and the
 * 48-hour undo window boundary.
 */
class RemittancePolicyTest {
    private val remittanceId = UUID.randomUUID()

    // ===== financial arithmetic =====

    @Test
    fun `net of subtracts compensation and expenses from gross`() {
        val net =
            RemittancePolicy.netOf(
                grossIncome = BigDecimal("1000.00"),
                totalCompensation = BigDecimal("250.50"),
                totalExpenses = BigDecimal("49.25"),
            )
        assertEquals(BigDecimal("700.25"), net)
    }

    @Test
    fun `net of with empty components is the gross income`() {
        val net =
            RemittancePolicy.netOf(
                grossIncome = BigDecimal("1234.56"),
                totalCompensation = BigDecimal.ZERO,
                totalExpenses = BigDecimal.ZERO,
            )
        assertEquals(BigDecimal("1234.56"), net)
    }

    @Test
    fun `net of keeps full decimal scale without rounding`() {
        val net =
            RemittancePolicy.netOf(
                grossIncome = BigDecimal("10.005"),
                totalCompensation = BigDecimal("0.0001"),
                totalExpenses = BigDecimal("0.0001"),
            )
        assertEquals(BigDecimal("10.0048"), net)
    }

    @Test
    fun `net of can go negative when costs exceed income`() {
        val net =
            RemittancePolicy.netOf(
                grossIncome = BigDecimal("100.00"),
                totalCompensation = BigDecimal("150.00"),
                totalExpenses = BigDecimal("25.00"),
            )
        assertEquals(BigDecimal("-75.00"), net)
    }

    @Test
    fun `sum folds amounts at full scale and empty list is zero`() {
        assertEquals(BigDecimal.ZERO, RemittancePolicy.sum(emptyList()))
        assertEquals(
            BigDecimal("3.75"),
            RemittancePolicy.sum(listOf(BigDecimal("1.00"), BigDecimal("2.75"))),
        )
    }

    // ===== snapshot eligibility =====

    @Test
    fun `only SESSION remittances require an immutable snapshot`() {
        assertTrue(RemittancePolicy.requiresSnapshot(RemittanceType.SESSION))
        assertFalse(RemittancePolicy.requiresSnapshot(RemittanceType.PRODUCT))
    }

    // ===== submit transition =====

    @Test
    fun `submit accepts a DRAFT at the expected version`() {
        RemittancePolicy.assertSubmittable(RemittanceStatus.DRAFT, actualVersion = 3, expectedVersion = 3, remittanceId)
    }

    @Test
    fun `submit rejects a stale expected version as version mismatch`() {
        val error =
            assertFailsWith<VersionMismatchException> {
                RemittancePolicy.assertSubmittable(
                    RemittanceStatus.DRAFT,
                    actualVersion = 4,
                    expectedVersion = 3,
                    remittanceId,
                )
            }
        assertEquals("remittance", REMITTANCE_TABLE_NAME)
        assertTrue(checkNotNull(error.message).contains(remittanceId.toString()))
    }

    @Test
    fun `submit transition matrix rejects every non-DRAFT status`() {
        listOf(RemittanceStatus.SUBMITTED).forEach { status ->
            assertFailsWith<ValidationException> {
                RemittancePolicy.assertSubmittable(status, actualVersion = 1, expectedVersion = 1, remittanceId)
            }
        }
    }

    // ===== undo transition =====

    @Test
    fun `undo accepts a SUBMITTED row at the expected version`() {
        RemittancePolicy.assertUndoable(
            RemittanceStatus.SUBMITTED,
            actualVersion = 7,
            expectedVersion = 7,
            remittanceId,
        )
    }

    @Test
    fun `undo transition matrix rejects non-SUBMITTED status before version checks`() {
        val error =
            assertFailsWith<ValidationException> {
                RemittancePolicy.assertUndoable(
                    RemittanceStatus.DRAFT,
                    actualVersion = 9,
                    expectedVersion = 7,
                    remittanceId,
                )
            }
        assertEquals("Can only undo SUBMITTED remittances", error.message)
    }

    @Test
    fun `undo rejects a stale expected version as version mismatch`() {
        assertFailsWith<VersionMismatchException> {
            RemittancePolicy.assertUndoable(
                RemittanceStatus.SUBMITTED,
                actualVersion = 8,
                expectedVersion = 7,
                remittanceId,
            )
        }
    }

    // ===== draft-only mutations =====

    @Test
    fun `draft guard passes for DRAFT and names the mutation otherwise`() {
        RemittancePolicy.assertDraft(RemittanceStatus.DRAFT, "add lines to")
        val error =
            assertFailsWith<ValidationException> {
                RemittancePolicy.assertDraft(RemittanceStatus.SUBMITTED, "add lines to")
            }
        assertEquals("Can only add lines to DRAFT remittances", error.message)
    }

    // ===== undo window =====

    private fun at(hour: Long) = OffsetDateTime.of(2026, 6, 27, 8, 0, 0, 0, ZoneOffset.UTC).plusHours(hour)

    @Test
    fun `undo window allows up to and including exactly 48 hours`() {
        RemittancePolicy.assertWithinUndoWindow(submissionInstant = at(0), comparisonInstant = at(48))
    }

    @Test
    fun `undo window expires strictly after 48 hours`() {
        assertFailsWith<ValidationException> {
            RemittancePolicy.assertWithinUndoWindow(submissionInstant = at(0), comparisonInstant = at(49))
        }
    }

    // ===== range membership (#483) =====

    @Test
    fun `range guard accepts boundary dates inclusively`() {
        val start = LocalDate.of(2026, 7, 1)
        val end = LocalDate.of(2026, 7, 15)
        RemittancePolicy.assertDateInRange(start, start, end, "Source")
        RemittancePolicy.assertDateInRange(end, start, end, "Source")
        RemittancePolicy.assertDateInRange(LocalDate.of(2026, 7, 10), start, end, "Source")
    }

    @Test
    fun `range guard rejects dates outside the range`() {
        val start = LocalDate.of(2026, 7, 1)
        val end = LocalDate.of(2026, 7, 15)
        val before =
            assertFailsWith<ValidationException> {
                RemittancePolicy.assertDateInRange(LocalDate.of(2026, 6, 30), start, end, "Source")
            }
        assertTrue(checkNotNull(before.message).contains("outside remittance range"))
        assertFailsWith<ValidationException> {
            RemittancePolicy.assertDateInRange(LocalDate.of(2026, 7, 16), start, end, "Branch day")
        }
    }
}
