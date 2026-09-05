package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.PgStatStatementsView
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class SlowStatement(
    val query: String,
    val calls: Long,
    val meanExecTimeMs: Double,
    val maxExecTimeMs: Double,
    val totalExecTimeMs: Double,
)

/**
 * Slow-query visibility reads (#474). Exposed DSL over the pg_stat_statements
 * view (V3 migration) — the view is read-only, so this repository has no
 * mutators, no capability gate, and no audit rows. Rows accumulate only while
 * the server preloads pg_stat_statements (see docs/slow-query-runbook.md);
 * with tracking off the view is simply empty.
 */
object SlowQueryRepository {
    private const val TOP_SLOW_LIMIT = 20

    fun topSlow(limit: Int = TOP_SLOW_LIMIT): List<SlowStatement> =
        transaction {
            PgStatStatementsView
                .selectAll()
                .orderBy(PgStatStatementsView.meanExecTime to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    SlowStatement(
                        query = row[PgStatStatementsView.query],
                        calls = row[PgStatStatementsView.calls],
                        meanExecTimeMs = row[PgStatStatementsView.meanExecTime],
                        maxExecTimeMs = row[PgStatStatementsView.maxExecTime],
                        totalExecTimeMs = row[PgStatStatementsView.totalExecTime],
                    )
                }
        }
}
