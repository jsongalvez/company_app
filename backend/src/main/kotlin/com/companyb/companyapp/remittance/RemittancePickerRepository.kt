package com.companyb.companyapp.remittance

import com.companyb.companyapp.branchday.BranchDay
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

internal object RemittancePickerRepository {
    fun findSessionsInRangeInTransaction(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> =
        SessionTable
            .innerJoin(BranchDayTable, { SessionTable.branchDayId }, { BranchDayTable.id })
            .innerJoin(ClientTable, { SessionTable.clientId }, { ClientTable.id })
            .leftJoin(ActiveSessionVoidsView, { SessionTable.id }, { ActiveSessionVoidsView.sessionId })
            .selectAll()
            .where {
                (BranchDayTable.branchId eq branchId) and
                    (BranchDayTable.date greaterEq from) and
                    (BranchDayTable.date lessEq to) and
                    (SessionTable.sessionStatus eq SessionStatus.COMPLETED) and
                    (ActiveSessionVoidsView.sessionId.isNull())
            }.orderBy(
                BranchDayTable.date to SortOrder.ASC,
                SessionTable.createdAt to SortOrder.ASC,
                SessionTable.id to SortOrder.ASC,
            ).map { row ->
                RemittanceSessionPickerEntry(
                    id = row[SessionTable.id],
                    clientName =
                        buildClientName(
                            firstName = row[ClientTable.firstName],
                            middleName = row[ClientTable.middleName],
                            lastName = row[ClientTable.lastName],
                            suffix = row[ClientTable.suffix],
                        ),
                    bookedAt = row[SessionTable.bookedAt],
                    sessionStatus = row[SessionTable.sessionStatus],
                    finalPrice = row[SessionTable.finalPrice],
                )
            }

    fun findProductSalesInRangeInTransaction(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> =
        ProductSaleTable
            .innerJoin(BranchDayTable, { ProductSaleTable.branchDayId }, { BranchDayTable.id })
            // A sale with no session never joins, so walk-in sales pass the IS NULL filter.
            .leftJoin(
                ActiveSessionVoidsView,
                { ProductSaleTable.sessionId },
                { ActiveSessionVoidsView.sessionId },
            ).selectAll()
            .where {
                (BranchDayTable.branchId eq branchId) and
                    (BranchDayTable.date greaterEq from) and
                    (BranchDayTable.date lessEq to) and
                    ProductSaleTable.deletedAt.isNull() and
                    (ActiveSessionVoidsView.sessionId.isNull())
            }.orderBy(
                BranchDayTable.date to SortOrder.ASC,
                ProductSaleTable.soldAt to SortOrder.ASC,
                ProductSaleTable.id to SortOrder.ASC,
            ).map { row ->
                RemittanceProductSalePickerEntry(
                    id = row[ProductSaleTable.id],
                    productName = row[ProductSaleTable.productName],
                    quantity = row[ProductSaleTable.quantity],
                    totalAmountAtTime = row[ProductSaleTable.totalAmountAtTime],
                    soldAt = row[ProductSaleTable.soldAt],
                )
            }

    fun findBranchDaysInRangeInTransaction(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<BranchDay> =
        BranchDayTable
            .selectAll()
            .where {
                (BranchDayTable.branchId eq branchId) and
                    (BranchDayTable.date greaterEq from) and
                    (BranchDayTable.date lessEq to)
            }.orderBy(
                BranchDayTable.date to SortOrder.ASC,
                BranchDayTable.id to SortOrder.ASC,
            ).map { it.toBranchDay() }

    private fun buildClientName(
        firstName: String?,
        middleName: String?,
        lastName: String?,
        suffix: String?,
    ): String? {
        val parts = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }
        val suffixPart = suffix?.takeIf { it.isNotBlank() }
        val joined = parts.joinToString(" ").ifBlank { null }
        return when {
            joined == null -> suffixPart
            suffixPart == null -> joined
            else -> "$joined $suffixPart"
        }
    }

    private fun ResultRow.toBranchDay(): BranchDay =
        BranchDay(
            id = this[BranchDayTable.id],
            branchId = this[BranchDayTable.branchId],
            date = this[BranchDayTable.date],
            status = this[BranchDayTable.status],
        )
}

data class RemittanceSessionPickerEntry(
    val id: UUID,
    val clientName: String?,
    val bookedAt: OffsetDateTime?,
    val sessionStatus: SessionStatus,
    val finalPrice: BigDecimal,
)

data class RemittanceProductSalePickerEntry(
    val id: UUID,
    val productName: String,
    val quantity: Int,
    val totalAmountAtTime: BigDecimal,
    val soldAt: OffsetDateTime,
)
