package com.companyb.companyapp.session

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.session.BookingResponse
import com.companyb.companyapp.contracts.session.BookingStatus
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID

private const val BOOKING_LIST_LIMIT = 300

internal object VisitBookingRepository {
    private val creator = AppUserTable.alias("booking_creator")
    private val updater = AppUserTable.alias("booking_updater")

    /** Booked visits at a branch from [from] on, soonest first. */
    fun findUpcomingForBranch(
        branchId: UUID,
        from: LocalDate,
    ): List<BookingResponse> =
        transaction {
            list(
                (VisitBookingTable.branchId eq branchId) and (VisitBookingTable.visitDate greaterEq from) and
                    (VisitBookingTable.status eq BookingStatus.BOOKED.name),
                SortOrder.ASC,
            )
        }

    /** Every booking a client has, newest visit day first. */
    fun findForClient(clientId: UUID): List<BookingResponse> =
        transaction { list(VisitBookingTable.clientId eq clientId, SortOrder.DESC) }

    fun findResponse(id: UUID): BookingResponse? =
        transaction {
            list(VisitBookingTable.id eq id, SortOrder.ASC).singleOrNull()
        }

    fun clientExistsInTransaction(clientId: UUID): Boolean =
        ClientTable
            .selectAll()
            .where { ClientTable.id eq clientId }
            .any()

    fun findByIdInTransaction(id: UUID): VisitBooking? =
        VisitBookingTable
            .selectAll()
            .where { VisitBookingTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toBooking()

    /** True when inserted; false when a booking with this id already exists (a retry). */
    fun insertInTransaction(
        booking: VisitBooking,
        createdBy: UUID,
    ): Boolean =
        VisitBookingTable
            .insertIgnore {
                it[id] = booking.id
                it[branchId] = booking.branchId
                it[clientId] = booking.clientId
                it[visitDate] = booking.visitDate
                it[visitTime] = booking.visitTime
                it[note] = booking.note
                it[status] = booking.status.name
                it[VisitBookingTable.createdBy] = createdBy
            }.insertedCount > 0

    fun updateInTransaction(
        booking: VisitBooking,
        updatedBy: UUID,
    ) {
        VisitBookingTable.update({ VisitBookingTable.id eq booking.id }) {
            it[visitDate] = booking.visitDate
            it[visitTime] = booking.visitTime
            it[note] = booking.note
            it[status] = booking.status.name
            it[VisitBookingTable.updatedBy] = updatedBy
            it[updatedAt] = CurrentTimestampWithTimeZone
        }
    }

    private fun list(
        where: Op<Boolean>,
        order: SortOrder,
    ): List<BookingResponse> =
        VisitBookingTable
            .innerJoin(ClientTable, { VisitBookingTable.clientId }, { ClientTable.id })
            .innerJoin(BranchTable, { VisitBookingTable.branchId }, { BranchTable.id })
            .innerJoin(creator, { VisitBookingTable.createdBy }, { creator[AppUserTable.id] })
            .leftJoin(updater, { VisitBookingTable.updatedBy }, { updater[AppUserTable.id] })
            .selectAll()
            .where { where }
            .orderBy(VisitBookingTable.visitDate to order, VisitBookingTable.visitTime to SortOrder.ASC_NULLS_LAST)
            .limit(BOOKING_LIST_LIMIT)
            .map { it.toResponse() }

    private fun ResultRow.toResponse(): BookingResponse =
        BookingResponse(
            id = this[VisitBookingTable.id].toString(),
            branchId = this[VisitBookingTable.branchId].toString(),
            branchName = this[BranchTable.name],
            clientId = this[VisitBookingTable.clientId].toString(),
            clientName =
                listOfNotNull(this[ClientTable.firstName], this[ClientTable.lastName])
                    .joinToString(" ")
                    .ifBlank { "(details erased)" },
            clientPhone = this[ClientTable.phoneNumber],
            visitDate = this[VisitBookingTable.visitDate].toString(),
            visitTime = this[VisitBookingTable.visitTime]?.toString(),
            note = this[VisitBookingTable.note],
            status = BookingStatus.valueOf(this[VisitBookingTable.status]),
            createdByName = this[creator[AppUserTable.displayName]],
            createdAt = this[VisitBookingTable.createdAt].toString(),
            updatedByName = this.getOrNull(updater[AppUserTable.displayName]),
            updatedAt = this[VisitBookingTable.updatedAt]?.toString(),
        )

    private fun ResultRow.toBooking(): VisitBooking =
        VisitBooking(
            id = this[VisitBookingTable.id],
            branchId = this[VisitBookingTable.branchId],
            clientId = this[VisitBookingTable.clientId],
            visitDate = this[VisitBookingTable.visitDate],
            visitTime = this[VisitBookingTable.visitTime],
            note = this[VisitBookingTable.note],
            status = BookingStatus.valueOf(this[VisitBookingTable.status]),
        )
}
