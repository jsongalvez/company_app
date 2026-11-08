package com.companyb.companyapp.contracts.authorization

import com.companyb.companyapp.authorization.Act
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
    const val VIEW_COMMISSION = "VIEW_COMMISSION"
    const val ERASE_CLIENT = "ERASE_CLIENT"
    const val RECEIVE_NEXT_APPOINTMENT_ALERTS = "RECEIVE_NEXT_APPOINTMENT_ALERTS"
}

@Serializable
enum class CapabilityContextType { GLOBAL, BRANCH, BRANCH_DAY, MEDICAL_MISSION, PROVINCIAL_TOUR }

@Serializable
enum class CapabilitySourceType { RELIEF_ACCESS, MEDICAL_MISSION_DELEGATE, ROLE }

@Serializable
data class UserCapabilityResponse(
    val capabilityCode: String,
    val contextType: CapabilityContextType,
    val contextId: String,
    val sourceType: CapabilitySourceType,
)

/** What a person [may] do at a place, and which acts' routes they may [reads] (GET) there. */
@Serializable
data class ActsResponse(
    val may: List<Act>,
    val reads: List<Act>,
)

/** The acts a person gains at one branch or one branch day, on top of what they hold everywhere. */
@Serializable
data class PlaceActsResponse(
    val id: String,
    val may: List<Act>,
    val reads: List<Act>,
)

/**
 * Who may do what, as the server decides it. At a place, a person holds [everywhere], plus the
 * place's branch in [branches], plus its day in [days]. A branch or day not listed adds nothing.
 */
@Serializable
data class RightsResponse(
    val everywhere: ActsResponse,
    val branches: List<PlaceActsResponse>,
    val days: List<PlaceActsResponse>,
)
