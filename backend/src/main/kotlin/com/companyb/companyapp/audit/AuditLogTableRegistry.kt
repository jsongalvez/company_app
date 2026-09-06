package com.companyb.companyapp.audit

/**
 * Single backend source listing the audited tables (DB table name -> readable label).
 *
 * This is the contract of the audit write-sites: every table that gets an
 * [AuditLog.record*] call must be registered here, or it is invisible to
 * the Audit Log UI (the #122 table-list endpoint serves this registry, NOT
 * `DISTINCT table_name` from live data — a freshly audited table with zero rows
 * would otherwise be missing until its first write, per #104 D4).
 *
 * New audited tables must register here at their write-site.
 */
object AuditLogTableRegistry {
    data class AuditedTableEntry(
        val tableName: String,
        val label: String,
    )

    val tables: List<AuditedTableEntry> =
        listOf(
            AuditedTableEntry("allowance", "Allowance"),
            AuditedTableEntry("app_user", "User"),
            AuditedTableEntry("attendance", "Attendance"),
            AuditedTableEntry("branch", "Branch"),
            AuditedTableEntry("branch_day", "Branch Day"),
            AuditedTableEntry("branch_inventory", "Inventory"),
            AuditedTableEntry("client", "Client"),
            AuditedTableEntry("commission_manual_inclusion", "Commission Inclusion"),
            AuditedTableEntry("compensation", "Compensation"),
            AuditedTableEntry("concern", "Concern"),
            AuditedTableEntry("credential_token", "Credential Token"),
            AuditedTableEntry("expense", "Expense"),
            AuditedTableEntry("grant_relief_access", "Relief Access"),
            AuditedTableEntry("inventory_movement", "Inventory Movement"),
            AuditedTableEntry("medical_mission_delegate", "Delegate"),
            AuditedTableEntry("product", "Product"),
            AuditedTableEntry("product_category", "Product Category"),
            AuditedTableEntry("product_sale", "Product Sale"),
            AuditedTableEntry("relief_invite", "Relief Invite"),
            AuditedTableEntry("remittance", "Remittance"),
            AuditedTableEntry("remittance_day_breakdown", "Remittance Day"),
            AuditedTableEntry("remittance_financial_snapshot", "Remittance Snapshot"),
            AuditedTableEntry("remittance_line", "Remittance Line"),
            AuditedTableEntry("session", "Session"),
            AuditedTableEntry("session_base_rate", "Base Rate"),
            AuditedTableEntry("session_concern", "Session Concern"),
            AuditedTableEntry("session_practitioner", "Practitioner"),
            AuditedTableEntry("session_void", "Session Void"),
            AuditedTableEntry("user_branch_assignment", "Branch Assignment"),
            AuditedTableEntry("user_role", "User Roles"),
        )
}
