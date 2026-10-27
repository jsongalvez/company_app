package com.companyb.companyapp.session

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.exception.ForbiddenException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** The parsed fields of a new session, as the caller sent them. */
data class NewSession(
    val id: UUID,
    val clientId: UUID,
    val branchId: UUID,
    val isWalkIn: Boolean,
    val requestedPractitionerId: UUID?,
    val finalPrice: BigDecimal,
    val remarks: String?,
    val otherConcerns: String?,
    val nextAppointmentDate: LocalDate?,
    val reason: String?,
)

/** What saving a session answers: the session and its concerns. */
data class SavedSession(
    val result: SessionCreateResult,
    val concerns: List<Concern>,
)

/**
 * Saving a session (POST /api/sessions): the branch check, the write and the concern read in one
 * transaction, with the branch and today's day read once and handed down.
 */
internal object SessionSaveService {
    private val logger = KotlinLogging.logger {}

    fun save(
        callerId: UUID,
        new: NewSession,
    ): SavedSession =
        transaction {
            val branch = BranchService.findById(new.branchId)
            val today = BranchDayService.requireBranchOrDayForTodayRow(callerId, new.branchId)
            val result =
                SessionService.create(
                    callerId = callerId,
                    id = new.id,
                    clientId = new.clientId,
                    branchId = new.branchId,
                    isWalkIn = new.isWalkIn,
                    requestedPractitionerId = new.requestedPractitionerId,
                    finalPrice = new.finalPrice,
                    remarks = new.remarks,
                    otherConcerns = new.otherConcerns,
                    nextAppointmentDate = new.nextAppointmentDate,
                    gatedBranchDayId = today?.id,
                    reason = new.reason,
                    knownBranchType = branch.branchType,
                    knownGatedBranchDay = today,
                )
            // A session just written has no concerns yet; only a repeat of an earlier save reads them.
            val concerns = if (result.created) emptyList() else concernsOfEarlierSave(callerId, new.id)
            SavedSession(result, concerns)
        }

    /** A repeat that lands after the day moved on may no longer be readable: acknowledge it with none. */
    private fun concernsOfEarlierSave(
        callerId: UUID,
        id: UUID,
    ): List<Concern> =
        try {
            SessionConcernService.getForSession(callerId, id)
        } catch (e: ForbiddenException) {
            logger.info { "[CREATE-SESSION] replay forbidden for $id, acking empty: ${e.message}" }
            emptyList()
        }
}
