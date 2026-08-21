package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.MeBranchResponse
import com.companyb.companyapp.dto.MeResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

object MeService {
    fun getMe(userId: UUID): MeResponse =
        transaction {
            val row = requireActiveUserInTransaction(userId)
            MeResponse(
                id = row[AppUserTable.id].toString(),
                username = row[AppUserTable.username],
                status =
                    com.companyb.companyapp.domain.UserStatus
                        .valueOf(row[AppUserTable.status].name),
                createdAt =
                    row[AppUserTable.createdAt]
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
        }.also { logger.info { "[GET-ME] Fetched user $userId" } }

    fun getBranches(userId: UUID): List<MeBranchResponse> {
        val today = BranchDayService.currentOperationalDate()
        return transaction {
            requireActiveUserInTransaction(userId)
            val assignedBranchIds =
                UserBranchAssignmentTable
                    .selectAll()
                    .where {
                        (UserBranchAssignmentTable.userId eq userId) and
                            (UserBranchAssignmentTable.endedAt.isNull())
                    }.map { it[UserBranchAssignmentTable.branchId] }
                    .toSet()
            val clockedInTodayBranchIds =
                AttendanceTable
                    .innerJoin(BranchDayTable, { AttendanceTable.branchDayId }, { BranchDayTable.id })
                    .selectAll()
                    .where {
                        (AttendanceTable.userId eq userId) and
                            (AttendanceTable.clockOut.isNull()) and
                            (BranchDayTable.date eq today)
                    }.map { it[BranchDayTable.branchId] }
                    .distinct()
            val branchIds = assignedBranchIds + clockedInTodayBranchIds
            val branchRows =
                if (branchIds.isEmpty()) {
                    emptyList()
                } else {
                    BranchTable
                        .selectAll()
                        .where { BranchTable.id inList branchIds }
                        .orderBy(BranchTable.name to SortOrder.ASC, BranchTable.id to SortOrder.ASC)
                        .toList()
                }
            branchRows.map { row ->
                val branchId = row[BranchTable.id]
                MeBranchResponse(
                    branchId = branchId.toString(),
                    branchName = row[BranchTable.name],
                    branchType = row[BranchTable.branchType],
                    clockInStatus =
                        when {
                            branchId in clockedInTodayBranchIds -> BranchClockInStatus.CLOCKED_IN_HERE
                            clockedInTodayBranchIds.isNotEmpty() -> BranchClockInStatus.CLOCKED_IN_ELSEWHERE
                            else -> BranchClockInStatus.NOT_CLOCKED_IN
                        },
                    isRelief = branchId !in assignedBranchIds,
                )
            }
        }.also { logger.info { "[GET-ME-BRANCHES] Fetched ${it.size} branch(es) for user $userId" } }
    }

    fun getCapabilities(userId: UUID): List<UserCapabilityResponse> = CapabilityService.getCapabilitiesForUser(userId)

    @Suppress("UnreachableCode")
    private fun requireActiveUserInTransaction(userId: UUID): ResultRow {
        val row =
            AppUserTable
                .selectAll()
                .where { AppUserTable.id eq userId }
                .singleOrNull()
                ?: throw NotFoundException("User not found")
        if (row[AppUserTable.status] != UserStatus.ACTIVE) {
            throw ForbiddenException("User account is inactive")
        }
        return row
    }
}
