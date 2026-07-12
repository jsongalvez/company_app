package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.Client
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object ClientService {
    private val VALID_GENDERS = setOf("M", "F")
    private const val EDIT_BRANCH_DATA = "EDIT_BRANCH_DATA"

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        firstName: String,
        lastName: String,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: String,
        age: Int,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): ClientCreateResult {
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        if (cleanFirst.isBlank()) {
            throw BadRequestResponse("First name is required")
        }
        if (cleanLast.isBlank()) {
            throw BadRequestResponse("Last name is required")
        }
        if (gender !in VALID_GENDERS) {
            throw BadRequestResponse("Gender must be 'M' or 'F'")
        }
        val hasBothBp = systolicBp != null && diastolicBp != null
        val hasNone = systolicBp == null && diastolicBp == null
        if (!hasBothBp && !hasNone) {
            throw BadRequestResponse(
                "Both systolic and diastolic blood pressure must be provided together or not at all",
            )
        }

        return ClientRepository.create(
            id = id,
            firstName = cleanFirst,
            lastName = cleanLast,
            middleName = middleName?.trim()?.takeIf { it.isNotEmpty() },
            suffix = suffix?.trim()?.takeIf { it.isNotEmpty() },
            phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
            address = address?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A",
            gender = gender,
            age = age,
            systolicBp = systolicBp,
            diastolicBp = diastolicBp,
            medicalConditions = medicalConditions?.trim()?.takeIf { it.isNotEmpty() },
            changedBy = callerId,
        )
    }

    fun search(query: String): List<Client> {
        val q = query.trim()
        if (q.isEmpty()) {
            throw BadRequestResponse("Search query is required")
        }
        return ClientRepository.search(q)
    }

    fun findById(clientId: UUID): Client =
        ClientRepository.findById(clientId) ?: throw NotFoundResponse("Client not found")

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount", "CyclomaticComplexMethod")
    fun update(
        callerId: UUID,
        clientId: UUID,
        firstName: String?,
        lastName: String?,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: String?,
        age: Int?,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): Client {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("EDIT_BRANCH_DATA capability required to update clients")
        }

        if (firstName != null && firstName.trim().isBlank()) {
            throw BadRequestResponse("First name cannot be blank")
        }
        if (lastName != null && lastName.trim().isBlank()) {
            throw BadRequestResponse("Last name cannot be blank")
        }
        if (gender != null && gender !in VALID_GENDERS) {
            throw BadRequestResponse("Gender must be 'M' or 'F'")
        }
        val hasBothBp = systolicBp != null && diastolicBp != null
        val hasNone = systolicBp == null && diastolicBp == null
        if (!hasBothBp && !hasNone) {
            throw BadRequestResponse(
                "Both systolic and diastolic blood pressure must be provided together or not at all",
            )
        }

        val updated =
            ClientRepository.update(
                clientId = clientId,
                firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
                lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
                middleName = middleName?.trim()?.takeIf { it.isNotEmpty() },
                suffix = suffix?.trim()?.takeIf { it.isNotEmpty() },
                phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                address = address?.trim()?.takeIf { it.isNotEmpty() },
                gender = gender,
                age = age,
                systolicBp = systolicBp,
                diastolicBp = diastolicBp,
                medicalConditions = medicalConditions?.trim()?.takeIf { it.isNotEmpty() },
                changedBy = callerId,
            )
        return updated ?: throw NotFoundResponse("Client not found")
    }

    fun anonymize(
        callerId: UUID,
        clientId: UUID,
    ) {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = EDIT_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            throw ForbiddenResponse("EDIT_BRANCH_DATA capability required to anonymize clients")
        }

        val updated = ClientRepository.anonymize(clientId, callerId)
        if (!updated) {
            throw NotFoundResponse("Client not found")
        }
    }
}
