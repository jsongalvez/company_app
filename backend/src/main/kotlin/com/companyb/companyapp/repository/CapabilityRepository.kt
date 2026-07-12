package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.ActiveUserCapabilitiesView
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilityTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object CapabilityRepository {
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
}
