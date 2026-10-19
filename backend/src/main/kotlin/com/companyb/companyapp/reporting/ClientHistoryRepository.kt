package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.contracts.reporting.ClientHistoryResponse
import com.companyb.companyapp.contracts.reporting.ClientSaleItem
import com.companyb.companyapp.contracts.reporting.ClientSessionItem
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.session.ConcernTable
import com.companyb.companyapp.session.SessionConcernTable
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private const val HISTORY_LIMIT = 200

/** A client's past sessions and purchases: "I can't see the client's past history". */
internal object ClientHistoryRepository {
    fun load(clientId: UUID): ClientHistoryResponse =
        transaction {
            val sessions = findSessions(clientId)
            ClientHistoryResponse(
                sessions = sessions,
                sales = findSales(clientId, sessions.map { UUID.fromString(it.id) }),
            )
        }

    private fun findSessions(clientId: UUID): List<ClientSessionItem> {
        val rows =
            SessionTable
                .innerJoin(BranchDayTable, { SessionTable.branchDayId }, { BranchDayTable.id })
                .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
                .selectAll()
                .where { SessionTable.clientId eq clientId }
                .orderBy(BranchDayTable.date to SortOrder.DESC, SessionTable.bookedAt to SortOrder.DESC_NULLS_LAST)
                .limit(HISTORY_LIMIT)
                .toList()
        val ids = rows.map { it[SessionTable.id] }
        val practitioners = practitionersOf(ids)
        val concerns = concernsOf(ids)
        return rows.map {
            val id = it[SessionTable.id]
            ClientSessionItem(
                id = id.toString(),
                date = it[BranchDayTable.date].toString(),
                branchName = it[BranchTable.name],
                status = it[SessionTable.sessionStatus],
                type = it[SessionTable.sessionType],
                voided = it[SessionTable.isVoided],
                price = money(it[SessionTable.finalPrice]),
                practitioners = practitioners[id].orEmpty(),
                concerns =
                    concerns[id].orEmpty() +
                        listOfNotNull(it[SessionTable.otherConcerns]?.takeIf { o -> o.isNotBlank() }),
                remarks = it[SessionTable.remarks],
            )
        }
    }

    private fun practitionersOf(ids: List<UUID>): Map<UUID, List<String>> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            SessionPractitionerTable
                .innerJoin(AppUserTable, { SessionPractitionerTable.practitionerId }, { AppUserTable.id })
                .selectAll()
                .where { SessionPractitionerTable.sessionId inList ids }
                .orderBy(SessionPractitionerTable.slotAtTime to SortOrder.ASC)
                .groupBy({ it[SessionPractitionerTable.sessionId] }, { it[AppUserTable.displayName] })
        }

    private fun concernsOf(ids: List<UUID>): Map<UUID, List<String>> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            SessionConcernTable
                .innerJoin(ConcernTable, { SessionConcernTable.concernId }, { ConcernTable.id })
                .selectAll()
                .where { SessionConcernTable.sessionId inList ids }
                .orderBy(ConcernTable.label to SortOrder.ASC)
                .groupBy({ it[SessionConcernTable.sessionId] }, { it[ConcernTable.label] })
        }

    private fun findSales(
        clientId: UUID,
        sessionIds: List<UUID>,
    ): List<ClientSaleItem> =
        ProductSaleTable
            .innerJoin(BranchDayTable, { ProductSaleTable.branchDayId }, { BranchDayTable.id })
            .innerJoin(BranchTable, { BranchDayTable.branchId }, { BranchTable.id })
            .selectAll()
            .where {
                val own = ProductSaleTable.clientId eq clientId
                if (sessionIds.isEmpty()) own else own or (ProductSaleTable.sessionId inList sessionIds)
            }.orderBy(ProductSaleTable.soldAt to SortOrder.DESC)
            .limit(HISTORY_LIMIT)
            .map {
                ClientSaleItem(
                    id = it[ProductSaleTable.id].toString(),
                    date = it[BranchDayTable.date].toString(),
                    branchName = it[BranchTable.name],
                    productName = it[ProductSaleTable.productName],
                    quantity = it[ProductSaleTable.quantity],
                    total = money(it[ProductSaleTable.totalAmountAtTime]),
                    sessionId = it[ProductSaleTable.sessionId]?.toString(),
                )
            }

    private fun money(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
