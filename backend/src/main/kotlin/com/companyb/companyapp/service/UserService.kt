package com.companyb.companyapp.service

import com.companyb.companyapp.auth.DenyList
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.UserRepository
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
        val updated = UserRepository.deactivate(targetUserId, callerId)
        if (!updated) {
            throw NotFoundException("User not found")
        }
        DenyList.deny(targetUserId)
        logger.info { "[DEACTIVATE] User deactivated and added to deny list" }
    }
}
