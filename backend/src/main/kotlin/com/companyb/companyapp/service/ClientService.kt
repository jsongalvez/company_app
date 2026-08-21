package com.companyb.companyapp.service

import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ClientCreateParams
import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.ClientRepository
import com.companyb.companyapp.repository.ClientUpdateParams
import com.companyb.companyapp.repository.acquireClientLock
import com.companyb.companyapp.repository.hasActivePendingSessionInTransaction
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.ClientTable
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
        ) { client ->
            AuditLogRepository.recordInsert(
                tableName = ClientTable.tableName,
                recordId = client.id,
                changedBy = callerId,
                fields = ClientTable.auditFields(client),
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
                AuditLogRepository.recordUpdate(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    before = old,
                    after = client,
                    changedBy = callerId,
                    auditFields = ClientTable::auditFields,
                )
            }
        return updated ?: throw NotFoundException("Client not found")
    }

    @Suppress("ThrowsCount")
    fun anonymize(
        callerId: UUID,
        clientId: UUID,
    ) {
        val old = ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")
        val updated =
            ClientRepository.anonymize(
                clientId = clientId,
                guardFn = {
                    acquireClientLock(clientId)
                    if (hasActivePendingSessionInTransaction(clientId)) {
                        throw ConflictException(
                            "Client has an active PENDING session; complete or cancel it before anonymizing",
                        )
                    }
                },
            ) { client ->
                AuditLogRepository.recordUpdate(
                    tableName = ClientTable.tableName,
                    recordId = clientId,
                    before = old,
                    after = client,
                    changedBy = callerId,
                    auditFields = ClientTable::auditFields,
                )
            }
        if (!updated) {
            throw NotFoundException("Client not found")
        }
    }
}
