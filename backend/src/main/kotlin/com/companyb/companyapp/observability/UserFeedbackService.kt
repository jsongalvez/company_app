package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.FeedbackStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** Problems and ideas sent from the app, kept in the database for triage. */
object UserFeedbackService {
    private val logger = KotlinLogging.logger {}

    fun send(note: NewUserFeedback): UserFeedback =
        transaction { UserFeedbackRepository.insertInTransaction(note) }.also {
            logger.info {
                "[FEEDBACK] ${it.kind} ${it.id.toString().maskUUID()} from ${it.userId.toString().maskUUID()}"
            }
        }

    fun list(status: FeedbackStatus?): List<UserFeedback> = UserFeedbackRepository.list(status)

    fun setStatus(
        id: UUID,
        status: FeedbackStatus,
    ): UserFeedback =
        transaction { UserFeedbackRepository.setStatusInTransaction(id, status) }
            ?: throw NotFoundException("Feedback not found")
}
