package com.companyb.companyapp.contracts.client

import kotlinx.serialization.Serializable

/**
 * #876 — [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `shared/AGENTS.md`), not a third gender: it marks a server value this client predates.
 * Never persisted, never sent.
 */
@Serializable
enum class Gender { M, F, UNKNOWN }

@Serializable
data class CreateClientRequest(
    val id: String,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val suffix: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val gender: Gender,
    val age: Int,
    val systolicBp: Short? = null,
    val diastolicBp: Short? = null,
    val medicalConditions: String? = null,
)

@Serializable
data class UpdateClientRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val suffix: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val gender: Gender? = null,
    val age: Int? = null,
    val systolicBp: Short? = null,
    val diastolicBp: Short? = null,
    val medicalConditions: String? = null,
    /**
     * Explicit clear intent per field name (#523). A present value means set, an absent
     * value means unchanged, a [ClientPatchField] entry here means clear (optional text
     * to null, address to its N/A default, BP only as a pair). Required
     * firstName/lastName/gender/age cannot be cleared. Explicit JSON nulls decode the
     * same as absent — only this set clears.
     */
    val clearFields: Set<String> = emptySet(),
)

/**
 * Field names accepted in [UpdateClientRequest.clearFields] (#523). Narrow to the
 * existing client fields — not a repository-wide patch framework.
 */
object ClientPatchField {
    const val MIDDLE_NAME = "middleName"
    const val SUFFIX = "suffix"
    const val PHONE_NUMBER = "phoneNumber"
    const val ADDRESS = "address"
    const val MEDICAL_CONDITIONS = "medicalConditions"
    const val SYSTOLIC_BP = "systolicBp"
    const val DIASTOLIC_BP = "diastolicBp"

    val clearable: Set<String> =
        setOf(
            MIDDLE_NAME,
            SUFFIX,
            PHONE_NUMBER,
            ADDRESS,
            MEDICAL_CONDITIONS,
            SYSTOLIC_BP,
            DIASTOLIC_BP,
        )

    val required: Set<String> = setOf("firstName", "lastName", "gender", "age")
}

@Serializable
data class ClientResponse(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String?,
    val gender: Gender = Gender.UNKNOWN,
    val age: Int,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
    val sessionCount: Int,
)
