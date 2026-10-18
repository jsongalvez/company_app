package com.companyb.companyapp.identity

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.BranchClockInStatus
import com.companyb.companyapp.contracts.branch.MeBranchResponse
import com.companyb.companyapp.contracts.identity.MeResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.ActiveAttendanceResponse
import com.companyb.companyapp.contracts.workforce.ActiveShiftResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.validation.EmailPolicy
import com.companyb.companyapp.workforce.WorkforceReads
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
    private const val NAME_MAX = 50
    private const val EMAIL_MAX = 50

    fun getMe(userId: UUID): MeResponse =
        transaction {
            val user = requireActiveUserInTransaction(userId)
            MeResponse(
                id = user.id.toString(),
                username = user.username,
                displayName = user.displayName,
                email = user.email,
                status = user.status,
                createdAt = user.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                canViewAs = ViewAs.SUPERUSER_ROLE in RoleRepository.findRoleNamesForUserInTransaction(userId),
            )
        }.also { logger.info { "[GET-ME] Fetched user $userId" } }

    /** Self-service edit of the caller's own name and email, audited as a user update. */
    fun updateProfile(
        userId: UUID,
        displayName: String,
        email: String,
    ): MeResponse {
        val name = displayName.trim()
        val mail = email.trim()
        val problem =
            when {
                name.isEmpty() || name.length > NAME_MAX -> "Name must be 1 to $NAME_MAX characters"
                mail.length > EMAIL_MAX || !EmailPolicy.isValid(mail) -> "Email is invalid"
                else -> null
            }
        if (problem != null) throw ValidationException(problem)
        val updated =
            transaction {
                val before = requireActiveUserInTransaction(userId)
                if (MeRepository.emailTakenByOther(userId, mail)) {
                    throw ConflictException("Another account already uses this email")
                }
                MeRepository.updateProfileInTransaction(userId, name, mail)
                ProfileAudit.updated(userId, before, name, mail)
                before.copy(displayName = name, email = mail)
            }
        logger.info { "[UPDATE-ME] Updated profile for user $userId" }
        return MeResponse(
            id = updated.id.toString(),
            username = updated.username,
            displayName = updated.displayName,
            email = updated.email,
            status = updated.status,
            createdAt = updated.createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

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

    /**
     * #669 — the caller's authoritative active shift for the current operational day.
     * Read-only: resolves the day find-only through the workforce seam (never creates
     * days, never marks attendance, no audit row). A revoked assignment does not hide an
     * open window — resume is an attendance fact; access stays capability-gated.
     */
    fun getActiveAttendance(userId: UUID): ActiveAttendanceResponse {
        val today = BranchDayService.currentOperationalDate()
        return transaction {
            requireActiveUserInTransaction(userId)
            val shift = WorkforceReads.findActiveShiftInTransaction(userId, today)
            ActiveAttendanceResponse(
                shift =
                    shift?.let {
                        ActiveShiftResponse(
                            attendanceId = it.attendanceId.toString(),
                            branchId = it.branchId.toString(),
                            branchName = it.branchName,
                            branchDayId = it.branchDayId.toString(),
                            date = it.date.toString(),
                            isRelief = it.isRelief,
                        )
                    },
            )
        }.also { logger.info { "[GET-ME-ACTIVE-ATTENDANCE] Fetched active shift for user $userId" } }
    }

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

/** Audit seam for self-service profile edits: the user-row table name stays behind it. */
internal object ProfileAudit {
    fun updated(
        userId: UUID,
        before: MeRepository.MeUser,
        displayName: String,
        email: String,
    ) = AuditLog.recordUpdate(
        tableName = AppUserTable.tableName,
        recordId = userId,
        changedBy = userId,
        oldFields = mapOf("displayName" to before.displayName, "email" to before.email),
        newFields = mapOf("displayName" to displayName, "email" to email),
    )
}
