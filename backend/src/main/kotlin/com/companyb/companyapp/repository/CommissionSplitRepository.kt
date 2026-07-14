package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.CommissionSplit
import com.companyb.companyapp.repository.model.CommissionSplitTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

private val logger = KotlinLogging.logger {}

object CommissionSplitRepository {
    fun findByBranchDayId(branchDayId: UUID): List<CommissionSplit> =
        transaction {
            CommissionSplitTable
                .selectAll()
                .where { CommissionSplitTable.branchDayId eq branchDayId }
                .map { it.toCommissionSplit() }
        }.also {
            logger.info { "[COMMISSION-SPLIT] Found ${it.size} splits for branchDay=$branchDayId" }
        }

    fun replaceForBranchDay(
        branchDayId: UUID,
        splits: Map<UUID, BigDecimal>,
    ) {
        transaction {
            CommissionSplitTable.deleteWhere {
                CommissionSplitTable.branchDayId eq branchDayId
            }

            splits.forEach { (userId, amount) ->
                CommissionSplitTable.insert {
                    it[CommissionSplitTable.branchDayId] = branchDayId
                    it[CommissionSplitTable.userId] = userId
                    it[CommissionSplitTable.amount] = amount
                }
            }
        }.also {
            logger.info { "[COMMISSION-SPLIT] Replaced splits for branchDay=$branchDayId with ${splits.size} entries" }
        }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toCommissionSplit(): CommissionSplit =
        CommissionSplit(
            id = this[CommissionSplitTable.id],
            branchDayId = this[CommissionSplitTable.branchDayId],
            userId = this[CommissionSplitTable.userId],
            amount = this[CommissionSplitTable.amount],
        )
}
