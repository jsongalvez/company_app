package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

// #566 moved identity/authorization/branch/branchday/session/workforce enums to
// contracts/<owner> (UserStatus, DayStatus, ReliefAccessStatus, CapabilityContextType,
// CapabilitySourceType). This umbrella keeps only the finance/commerce/remittance/audit/
// incident families for #567, which eliminates the remainder.

@Serializable
enum class RemittanceType { SESSION, PRODUCT }

@Serializable
enum class RemittanceMethod { BANK_TRANSFER, HANDED_TO_ACCOUNTANT }

@Serializable
enum class RemittanceStatus { DRAFT, SUBMITTED }

@Serializable
enum class RemittanceLineType { SESSION, PRODUCT_SALE }

@Serializable
enum class ExpenseCategory {
    PANTRY,
    COMMUNICATION,
    WATER,
    TRANSPORTATION,
    ELECTRICITY,
    RENTAL,
    OFFICE_SUPPLIES,
    FURNITURE_FIXTURES,
    MISCELLANEOUS,
}

@Serializable
enum class InventoryMovementReason { RESTOCK, SALE, TESTER, SAMPLE, MISSING, ADJUSTMENT }

@Serializable
enum class AuditAction { INSERT, UPDATE, DELETE }

/** #475 — origin of a triage-ready incident packet; the packet shape is identical either way. */
@Serializable
enum class IncidentSource { USER_REPORT, AUTO_5XX }
