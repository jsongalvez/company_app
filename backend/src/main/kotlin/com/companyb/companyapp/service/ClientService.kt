package com.companyb.companyapp.service

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ClientCreateParams
import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.ClientUpdateParams
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.ClientTable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
        ) { client ->
            AuditLogRepository.recordInsert(ClientTable.tableName, client, callerId)
        }

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
        val old = ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")
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
                ),
            ) { client ->
                AuditLogRepository.recordUpdate(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    oldFields =
                        mapOf(
                            "firstName" to (old.firstName ?: ""),
                            "lastName" to (old.lastName ?: ""),
                        ),
                    newFields =
                        mapOf(
                            "firstName" to (client.firstName ?: ""),
                            "lastName" to (client.lastName ?: ""),
                        ),
                    changedBy = callerId,
                )
            }
        return updated ?: throw NotFoundException("Client not found")
    }

    fun anonymize(
        callerId: UUID,
        clientId: UUID,
    ) {
        val old = ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")
        val updated =
            ClientRepository.anonymize(clientId) { client ->
                AuditLogRepository.recordUpdate(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    oldFields =
                        mapOf(
                            "firstName" to (old.firstName ?: "null"),
                            "lastName" to (old.lastName ?: "null"),
                            "deletedAt" to (old.deletedAt?.toString() ?: "null"),
                        ),
                    newFields =
                        mapOf(
                            "firstName" to "null",
                            "lastName" to "null",
                            "deletedAt" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        ),
                    changedBy = callerId,
                )
            }
        if (!updated) {
            throw NotFoundException("Client not found")
        }
    }
}
