package com.companyb.companyapp.domain

import kotlinx.serialization.Serializable

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
enum class DayStatus { OPEN, PAST, REMITTED }

@Serializable
enum class UserStatus { ACTIVE, INACTIVE }

@Serializable
enum class CapabilityContextType { GLOBAL, BRANCH, BRANCH_DAY, MEDICAL_MISSION, PROVINCIAL_TOUR }

@Serializable
enum class CapabilitySourceType { RELIEF_ACCESS, MEDICAL_MISSION_DELEGATE, MANUAL_OVERRIDE, SYSTEM, ROLE }
