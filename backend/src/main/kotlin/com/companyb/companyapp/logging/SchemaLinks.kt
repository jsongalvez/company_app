package com.companyb.companyapp.logging

/**
 * What a statement's text can't show about the schema: the tables under each view, and the tables
 * whose rows a delete changes through a foreign key. `SchemaLinksPostgresTest` checks both against
 * the migrated catalog, so a new view or cascade fails there until it is listed here.
 */
object SchemaLinks {
    val VIEW_TABLES: Map<String, Set<String>> =
        mapOf(
            "active_session_voids" to setOf("session_void"),
            "daily_sales_summary" to
                setOf(
                    "branch_day",
                    "session",
                    "session_void",
                    "compensation",
                    "expense",
                    "product_sale",
                    "commission_split",
                ),
            "monthly_remittance_summary" to setOf("remittance", "remittance_financial_snapshot", "remittance_line"),
        )

    val DELETE_CASCADES: Map<String, Set<String>> =
        mapOf(
            "user_feedback" to setOf("feedback_triage"),
            "agent_feedback" to setOf("feedback_triage"),
        )

    /** [tables] with each view replaced by the tables under it. */
    fun underViews(tables: Set<String>): Set<String> = tables.flatMap { VIEW_TABLES[it] ?: setOf(it) }.toSet()

    /** [tables] with the tables their deletes cascade to. */
    fun withCascades(tables: Set<String>): Set<String> = tables + tables.flatMap { DELETE_CASCADES[it].orEmpty() }
}
