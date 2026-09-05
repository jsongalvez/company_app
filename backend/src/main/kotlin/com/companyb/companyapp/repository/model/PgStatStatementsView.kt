package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table

/**
 * Read-only view over the pg_stat_statements extension (#474, V3 migration).
 * Never insert/update/delete against it. The `query` column holds normalized
 * statement text (literals replaced with $n placeholders), so bound values
 * never appear here.
 */
object PgStatStatementsView : Table("pg_stat_statements") {
    val query = text("query")
    val calls = long("calls")
    val totalExecTime = double("total_exec_time")
    val meanExecTime = double("mean_exec_time")
    val maxExecTime = double("max_exec_time")
}
