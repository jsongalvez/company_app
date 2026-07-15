package com.companyb.companyapp.service

import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter
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
                    if (row[AppUserTable.status] != UserStatus.ACTIVE) {
                        throw ForbiddenResponse("User account is inactive")
                    }
                    MeResponse(
                        id = row[AppUserTable.id].toString(),
                        username = row[AppUserTable.username],
                        status = row[AppUserTable.status].name,
                        createdAt =
                            row[AppUserTable.createdAt]
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    )
                } ?: throw NotFoundResponse("User not found")
        }.also { logger.info { "[GET-ME] Fetched user $userId" } }

    fun getCapabilities(userId: UUID): List<UserCapabilityResponse> = CapabilityService.getCapabilitiesForUser(userId)
}
