package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * User administration. Deactivation revokes all access immediately by both persisting
 * INACTIVE status (so future tokens fail DB authorization) and adding the user to the
 * in-memory [DenyList] (so already-issued tokens are rejected before any DB lookup).
 */
object UserService {
    private val logger = KotlinLogging.logger {}

    fun deactivate(
        callerId: UUID,
        targetUserId: UUID,
    ) {
        val user =
            UserRepository.deactivate(
                targetUserId,
                auditFn = { oldStatus, deactivated ->
                    AuditLogRepository.recordUpdate(
                        tableName = AppUserTable.tableName,
                        recordId = targetUserId,
                        oldFields = mapOf("status" to oldStatus),
                        newFields = mapOf("status" to UserStatus.INACTIVE.name),
                        changedBy = callerId,
                    )
                },
            )
        if (user == null) {
            throw NotFoundException("User not found")
        }
        DenyList.deny(targetUserId)
        logger.info { "[DEACTIVATE] User deactivated and added to deny list" }
    }
}
