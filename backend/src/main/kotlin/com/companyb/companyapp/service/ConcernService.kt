package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.Concern
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

object ConcernService {
    private val logger = KotlinLogging.logger {}

    fun listAll(): List<Concern> = ConcernRepository.findAll()

    fun getForSession(sessionId: UUID): List<Concern> {
        SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        return ConcernRepository.getConcernsForSession(sessionId)
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun addToSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
    ) {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        BranchDayService.assertEditable(session.branchDayId, callerId)

        val concern = ConcernRepository.findById(concernId) ?: throw NotFoundResponse("Concern not found")

        ConcernRepository.addToSession(
            sessionId = sessionId,
            concernId = concernId,
            changedBy = callerId,
        )

        logger.info { "[ADD-CONCERN] Added concern ${concern.label} to session $sessionId" }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun removeFromSession(
        callerId: UUID,
        sessionId: UUID,
        concernId: UUID,
    ) {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        BranchDayService.assertEditable(session.branchDayId, callerId)

        val concern = ConcernRepository.findById(concernId) ?: throw NotFoundResponse("Concern not found")

        ConcernRepository.removeFromSession(
            sessionId = sessionId,
            concernId = concernId,
            changedBy = callerId,
        )

        logger.info { "[REMOVE-CONCERN] Removed concern ${concern.label} from session $sessionId" }
    }

    @Suppress("ReturnCount", "ThrowsCount")
    fun promoteConcern(
        callerId: UUID,
        sessionId: UUID,
        label: String,
    ): Concern {
        val session = SessionRepository.findById(sessionId) ?: throw NotFoundResponse("Session not found")
        BranchDayService.assertEditable(session.branchDayId, callerId)

        val concern =
            ConcernRepository.create(
                id = UUID.randomUUID(),
                label = label,
                createdBy = callerId,
            )

        ConcernRepository.addToSession(
            sessionId = sessionId,
            concernId = concern.id,
            changedBy = callerId,
        )

        SessionRepository.updateOtherConcerns(
            sessionId = sessionId,
            otherConcerns = null,
            changedBy = callerId,
        )

        logger.info { "[PROMOTE-CONCERN] Promoted concern '$label' for session $sessionId, cleared other_concerns" }

        return concern
    }
}
