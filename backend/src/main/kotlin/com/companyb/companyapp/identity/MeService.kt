package com.companyb.companyapp.identity

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Me-surface reads (#324). SQL lives behind [MeRepository]; this service owns the active-user
 * policy (404 unknown / 403 inactive), asks the Branch Day authority for the operational day
 * (#322), and shapes responses. Each read owns one transaction.
 */
object MeService {
    fun getMe(userId: UUID): MeResponse =
        transaction {
            val user = requireActiveUserInTransaction(userId)
            MeResponse(
                id = user.id.toString(),
                username = user.username,
                displayName = user.displayName,
                status = user.status,
                createdAt = user.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
        }.also { logger.info { "[GET-ME] Fetched user $userId" } }

    fun getBranches(userId: UUID): List<MeBranchResponse> {
        val today = BranchDayService.currentOperationalDate()
        return transaction {
            requireActiveUserInTransaction(userId)
            val rows = MeRepository.findBranchRows(userId, today)
            val anyClockInToday = rows.any { it.clockedInHereToday }
            rows.map { row ->
                MeBranchResponse(
                    branchId = row.branchId.toString(),
                    branchName = row.branchName,
                    branchType = row.branchType,
                    clockInStatus =
                        when {
                            row.clockedInHereToday -> BranchClockInStatus.CLOCKED_IN_HERE
                            anyClockInToday -> BranchClockInStatus.CLOCKED_IN_ELSEWHERE
                            else -> BranchClockInStatus.NOT_CLOCKED_IN
                        },
                    isRelief = !row.assigned,
                    assignmentId = row.assignmentId?.toString(),
                    slot = row.slot,
                )
            }
        }.also { logger.info { "[GET-ME-BRANCHES] Fetched ${it.size} branch(es) for user $userId" } }
    }

    fun getCapabilities(userId: UUID): List<UserCapabilityResponse> = CapabilityService.getCapabilitiesForUser(userId)

    private fun requireActiveUserInTransaction(userId: UUID): MeRepository.MeUser {
        val user =
            MeRepository.findUser(userId)
                ?: throw NotFoundException("User not found")
        if (user.status != UserStatus.ACTIVE) {
            throw ForbiddenException("User account is inactive")
        }
        return user
    }
}
