package com.companyb.companyapp.contracts.authorization

import kotlinx.serialization.Serializable

object CapabilityCodes {
    const val VIEW_BRANCH_DATA = "VIEW_BRANCH_DATA"
    const val EDIT_BRANCH_DATA = "EDIT_BRANCH_DATA"
    const val EDIT_PAST_DAY = "EDIT_PAST_DAY"
    const val VOID_SESSION = "VOID_SESSION"
    const val SUBMIT_REMITTANCE = "SUBMIT_REMITTANCE"
    const val ASSIGN_COMPENSATION = "ASSIGN_COMPENSATION"
    const val MANAGE_PRODUCTS = "MANAGE_PRODUCTS"
    const val MANAGE_CATALOG = "MANAGE_CATALOG"
    const val MANAGE_USERS = "MANAGE_USERS"
    const val ASSIGN_DELEGATE = "ASSIGN_DELEGATE"
    const val RECEIVE_NEXT_APPOINTMENT_ALERTS = "RECEIVE_NEXT_APPOINTMENT_ALERTS"
}

@Serializable
enum class CapabilityContextType { GLOBAL, BRANCH, BRANCH_DAY, MEDICAL_MISSION, PROVINCIAL_TOUR }

@Serializable
enum class CapabilitySourceType { RELIEF_ACCESS, MEDICAL_MISSION_DELEGATE, MANUAL_OVERRIDE, SYSTEM, ROLE }

@Serializable
data class UserCapabilityResponse(
    val capabilityCode: String,
    val contextType: CapabilityContextType,
    val contextId: String,
    val sourceType: CapabilitySourceType,
)
