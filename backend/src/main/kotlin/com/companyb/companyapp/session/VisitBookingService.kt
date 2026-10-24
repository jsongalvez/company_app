package com.companyb.companyapp.session

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.session.BookingResponse
import com.companyb.companyapp.contracts.session.BookingStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private const val NOTE_MAX = 500

/** The fields a person can set on a booking. */
data class BookingInput(
    val visitDate: LocalDate,
    val visitTime: LocalTime?,
    val note: String?,
)

/**
 * Booked future visits (feedback e4ec363c): any branch member books a client for a day
 * without making a session for today. Each change is audited so the history shows who.
 */
object VisitBookingService {
    fun upcoming(branchId: UUID): List<BookingResponse> =
        VisitBookingRepository.findUpcomingForBranch(branchId, BranchDayService.currentOperationalDate())

    fun forClient(clientId: UUID): List<BookingResponse> = VisitBookingRepository.findForClient(clientId)

    /** Returns the booking and whether this call made it (false on a retry of the same id). */
    fun create(
        callerId: UUID,
        id: UUID,
        branchId: UUID,
        clientId: UUID,
        input: BookingInput,
    ): Pair<BookingResponse, Boolean> {
        val created =
            transaction {
                if (isRetry(id, branchId, clientId)) return@transaction false
                val booking =
                    VisitBooking(
                        id,
                        branchId,
                        clientId,
                        input.visitDate,
                        input.visitTime,
                        clean(input.note),
                        BookingStatus.BOOKED,
                    )
                validate(booking)
                VisitBookingRepository.insertInTransaction(booking, callerId).also { inserted ->
                    if (inserted) VisitBookingAudit.inserted(callerId, booking)
                }
            }
        return (VisitBookingRepository.findResponse(id) ?: throw NotFoundException("Booking not found")) to created
    }

    fun update(
        callerId: UUID,
        id: UUID,
        branchId: UUID,
        input: BookingInput,
        status: BookingStatus,
    ): BookingResponse {
        transaction {
            val before =
                VisitBookingRepository.findByIdInTransaction(id)?.takeIf { it.branchId == branchId }
                    ?: throw NotFoundException("Booking not found")
            val after =
                before.copy(
                    visitDate = input.visitDate,
                    visitTime = input.visitTime,
                    note = clean(input.note),
                    status = status,
                )
            if (after == before) return@transaction
            if (after.status == BookingStatus.BOOKED && after.visitDate != before.visitDate) validate(after)
            VisitBookingRepository.updateInTransaction(after, callerId)
            VisitBookingAudit.updated(callerId, before, after)
        }
        return VisitBookingRepository.findResponse(id) ?: throw NotFoundException("Booking not found")
    }

    /** True when [id] already names this same booking; a different one under the id is a conflict. */
    private fun isRetry(
        id: UUID,
        branchId: UUID,
        clientId: UUID,
    ): Boolean {
        val existing = VisitBookingRepository.findByIdInTransaction(id)
        if (existing != null && (existing.branchId != branchId || existing.clientId != clientId)) {
            throw ConflictException("That booking id is already used")
        }
        if (existing == null && !VisitBookingRepository.clientExistsInTransaction(clientId)) {
            throw NotFoundException("Client not found")
        }
        return existing != null
    }

    private fun clean(note: String?): String? = note?.trim()?.ifEmpty { null }

    private fun validate(booking: VisitBooking) {
        if (booking.visitDate.isBefore(BranchDayService.currentOperationalDate())) {
            throw ValidationException("Pick today or a later day")
        }
        if ((booking.note?.length ?: 0) > NOTE_MAX) throw ValidationException("Note is too long (max $NOTE_MAX)")
    }
}

/** Booking audit vocabulary: rows commit with the change, so history shows who did what. */
internal object VisitBookingAudit {
    fun inserted(
        changedBy: UUID,
        booking: VisitBooking,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = changedBy, branchId = booking.branchId),
        row = AuditedRow(VisitBookingTable.tableName, booking.id),
        fields = VisitBookingTable.auditFields(booking),
    )

    fun updated(
        changedBy: UUID,
        before: VisitBooking,
        after: VisitBooking,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = changedBy, branchId = before.branchId),
        row = AuditedRow(VisitBookingTable.tableName, before.id),
        before = before,
        after = after,
        auditFields = VisitBookingTable::auditFields,
    )
}
