package com.companyb.companyapp.service

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.ClientCreateParams
import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.ClientUpdateParams
import com.companyb.companyapp.repository.model.Client
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object ClientService {
    private val logger = KotlinLogging.logger {}

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
        gender: Gender,
        age: Int,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): ClientCreateResult =
        ClientRepository.create(
            ClientCreateParams(
                id = id,
                firstName = firstName,
                lastName = lastName,
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
            ),
        )

    fun search(query: String): List<Client> = ClientRepository.search(query)

    fun findById(clientId: UUID): Client =
        ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")

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
        gender: Gender?,
        age: Int?,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): Client {
        val updated =
            ClientRepository.update(
                ClientUpdateParams(
                    clientId = clientId,
                    firstName = firstName?.takeIf { it.isNotEmpty() },
                    lastName = lastName?.takeIf { it.isNotEmpty() },
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
                ),
            )
        return updated ?: throw NotFoundException("Client not found")
    }

    fun anonymize(
        callerId: UUID,
        clientId: UUID,
    ) {
        val updated = ClientRepository.anonymize(clientId, callerId)
        if (!updated) {
            throw NotFoundException("Client not found")
        }
    }
}
