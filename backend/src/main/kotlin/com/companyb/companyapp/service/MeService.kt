package com.companyb.companyapp.service

import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.repository.model.ActiveUserCapabilitiesView
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.CapabilityTable
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object MeService {
    fun getMe(userId: UUID): MeResponse =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .singleOrNull()
                ?.let { row ->
                    MeResponse(
                        id = row[AppUserTable.id].toString(),
                        username = row[AppUserTable.username],
                        status = row[AppUserTable.status].name,
                    )
                } ?: throw NotFoundResponse("User not found")
        }.also { logger.info { "[GET-ME] Fetched user $userId" } }

    fun getCapabilities(userId: UUID): List<UserCapabilityResponse> =
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
        }.also { logger.info { "[GET-CAPABILITIES] Fetched ${it.size} capabilities for user $userId" } }
}
