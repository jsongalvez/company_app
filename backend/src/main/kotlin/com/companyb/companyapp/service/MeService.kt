package com.companyb.companyapp.service

import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
                        throw ForbiddenException("User account is inactive")
                    }
                    MeResponse(
                        id = row[AppUserTable.id].toString(),
                        username = row[AppUserTable.username],
                        status = row[AppUserTable.status].name,
                        createdAt =
                            row[AppUserTable.createdAt]
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    )
                } ?: throw NotFoundException("User not found")
        }.also { logger.info { "[GET-ME] Fetched user $userId" } }

    fun getCapabilities(userId: UUID): List<UserCapabilityResponse> = CapabilityService.getCapabilitiesForUser(userId)
}
