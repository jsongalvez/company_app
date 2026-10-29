package com.companyb.companyapp.session

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
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
    /** The day to add to; null means today. */
    val date: LocalDate? = null,
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
            val day = dayToAddTo(callerId, new)
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
                    gatedBranchDayId = day?.id,
                    reason = new.reason,
                    knownBranchType = branch.branchType,
                    knownGatedBranchDay = day,
                )
            // A session just written has no concerns yet; only a repeat of an earlier save reads them.
            val concerns = if (result.created) emptyList() else concernsOfEarlierSave(callerId, new.id)
            SavedSession(result, concerns)
        }

    /** Today's row (null when nothing happened yet today), or the earlier day the session goes to, made if missing. */
    private fun dayToAddTo(
        callerId: UUID,
        new: NewSession,
    ): BranchDay? {
        val today = BranchDayService.currentOperationalDate()
        val date = new.date ?: today
        if (date.isAfter(today)) throw ValidationException("A session can't be added to a later day")
        if (date == today) {
            val row = BranchDayService.findToday(new.branchId)
            Access.require(
                callerId,
                Act.WORK_DAY,
                row?.let { Place.Day(it.id, new.branchId) } ?: Place.Branch(new.branchId),
            )
            return row
        }
        val day = BranchDayService.resolveOrCreate(new.branchId, date)
        Access.require(callerId, Act.WORK_DAY, Place.Day(day.id, new.branchId))
        return day
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
