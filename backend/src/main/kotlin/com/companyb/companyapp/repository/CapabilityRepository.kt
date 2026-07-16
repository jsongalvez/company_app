package com.companyb.companyapp.repository

import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.repository.model.ActiveUserCapabilitiesView
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilityTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object CapabilityRepository {
    fun findIdByCode(code: String): UUID? =
        transaction {
            CapabilityTable
                .selectAll()
                .where { CapabilityTable.code eq code }
                .singleOrNull()
                ?.let { it[CapabilityTable.id] }
        }

    fun hasCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
    ): Boolean =
        transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).selectAll()
                .where {
                    (ActiveUserCapabilitiesView.userId eq userId) and
                        (CapabilityTable.code eq capabilityCode) and
                        (ActiveUserCapabilitiesView.contextType eq contextType) and
                        (ActiveUserCapabilitiesView.contextId eq contextId)
                }.empty()
                .not()
        }.also { granted ->
            logger.info { "[HAS-CAPABILITY] $capabilityCode ($contextType) granted=$granted" }
        }

    fun findCapabilitiesForUser(userId: UUID): List<UserCapabilityResponse> =
        transaction {
            ActiveUserCapabilitiesView
                .innerJoin(
                    CapabilityTable,
                    { ActiveUserCapabilitiesView.capabilityId },
                    { CapabilityTable.id },
                ).selectAll()
                .where { ActiveUserCapabilitiesView.userId eq userId }
                .map { row ->
                    UserCapabilityResponse(
                        capabilityCode = row[CapabilityTable.code],
                        contextType = row[ActiveUserCapabilitiesView.contextType].name,
                        contextId = row[ActiveUserCapabilitiesView.contextId].toString(),
                        sourceType = row[ActiveUserCapabilitiesView.sourceType].name,
                    )
                }
        }.also { logger.info { "[FIND-CAPABILITIES] Fetched ${it.size} capabilities for user $userId" } }
}
