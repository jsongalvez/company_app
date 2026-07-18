package com.companyb.companyapp.service

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ClientCreateParams
import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.ClientUpdateParams
import com.companyb.companyapp.repository.model.AuditAction
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
            AuditLogRepository.record(
                tableName = ClientTable.tableName,
                recordId = client.id,
                action = AuditAction.INSERT,
                changedBy = callerId,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to client.id.toString(),
                        "firstName" to (client.firstName ?: ""),
                        "lastName" to (client.lastName ?: ""),
                    ),
            )
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
                AuditLogRepository.record(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to (old.firstName ?: ""),
                            "lastName" to (old.lastName ?: ""),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to (client.firstName ?: ""),
                            "lastName" to (client.lastName ?: ""),
                        ),
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
                AuditLogRepository.record(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to (old.firstName ?: "null"),
                            "lastName" to (old.lastName ?: "null"),
                            "deletedAt" to (old.deletedAt?.toString() ?: "null"),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "firstName" to "null",
                            "lastName" to "null",
                            "deletedAt" to OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        ),
                )
            }
        if (!updated) {
            throw NotFoundException("Client not found")
        }
    }
}
