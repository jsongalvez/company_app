package com.companyb.companyapp.repository

/**
 * Single backend source listing the audited tables (DB table name -> readable label).
 *
 * This is the contract of the audit write-sites: every table that gets an
 * [AuditLogRepository.record*] call must be registered here, or it is invisible to
 * the Audit Log UI (the #122 table-list endpoint serves this registry, NOT
 * `DISTINCT table_name` from live data — a freshly audited table with zero rows
 * would otherwise be missing until its first write, per #104 D4).
 *
 * New audited tables must register here at their write-site.
 */
object AuditLogTableRegistry {
    data class AuditedTable(
        val tableName: String,
        val label: String,
    )

    val tables: List<AuditedTable> =
        listOf(
            AuditedTable("allowance", "Allowance"),
            AuditedTable("app_user", "User"),
            AuditedTable("attendance", "Attendance"),
            AuditedTable("branch", "Branch"),
            AuditedTable("branch_day", "Branch Day"),
            AuditedTable("branch_inventory", "Inventory"),
            AuditedTable("client", "Client"),
            AuditedTable("commission_manual_inclusion", "Commission Inclusion"),
            AuditedTable("compensation", "Compensation"),
            AuditedTable("concern", "Concern"),
            AuditedTable("expense", "Expense"),
            AuditedTable("grant_relief_access", "Relief Access"),
            AuditedTable("inventory_movement", "Inventory Movement"),
            AuditedTable("medical_mission_delegate", "Delegate"),
            AuditedTable("product", "Product"),
            AuditedTable("product_category", "Product Category"),
            AuditedTable("product_sale", "Product Sale"),
            AuditedTable("remittance", "Remittance"),
            AuditedTable("remittance_day_breakdown", "Remittance Day"),
            AuditedTable("remittance_financial_snapshot", "Remittance Snapshot"),
            AuditedTable("remittance_line", "Remittance Line"),
            AuditedTable("session", "Session"),
            AuditedTable("session_base_rate", "Base Rate"),
            AuditedTable("session_concern", "Session Concern"),
            AuditedTable("session_practitioner", "Practitioner"),
            AuditedTable("session_void", "Session Void"),
            AuditedTable("user_branch_assignment", "Branch Assignment"),
        )
}
