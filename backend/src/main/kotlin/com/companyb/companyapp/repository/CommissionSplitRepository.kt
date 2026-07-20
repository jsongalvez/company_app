package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.CommissionSplit
import com.companyb.companyapp.repository.model.CommissionSplitTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        auditFn: (List<CommissionSplit>) -> Unit = {},
    ) {
        transaction {
            CommissionSplitTable.deleteWhere {
                CommissionSplitTable.branchDayId eq branchDayId
            }

            val created = mutableListOf<CommissionSplit>()
            splits.forEach { (userId, amount) ->
                val insert =
                    CommissionSplitTable.insert {
                        it[CommissionSplitTable.branchDayId] = branchDayId
                        it[CommissionSplitTable.userId] = userId
                        it[CommissionSplitTable.amount] = amount
                    }
                val id = insert[CommissionSplitTable.id]
                created.add(CommissionSplit(id, branchDayId, userId, amount))
            }
            auditFn(created)
        }.also {
            logger.info { "[COMMISSION-SPLIT] Replaced splits for branchDay=$branchDayId with ${splits.size} entries" }
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toCommissionSplit(): CommissionSplit =
        CommissionSplit(
            id = this[CommissionSplitTable.id],
            branchDayId = this[CommissionSplitTable.branchDayId],
            userId = this[CommissionSplitTable.userId],
            amount = this[CommissionSplitTable.amount],
        )
}
