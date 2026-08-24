package com.companyb.companyapp.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditLogTableRegistryTest {
    @Test
    fun `registry contains all audited tables with readable labels`() {
        val registry = AuditLogTableRegistry.tables
        assertTrue(registry.isNotEmpty(), "registry must not be empty")
        assertEquals(
            registry.size,
            registry.map { it.tableName }.distinct().size,
            "table names must be unique",
        )
        assertEquals(
            registry.size,
            registry.map { it.label }.distinct().size,
            "labels must be unique",
        )
        assertTrue(
            registry.all { it.tableName.isNotBlank() && it.label.isNotBlank() },
            "table names and labels must not be blank",
        )
    }

    @Test
    fun `registry covers the known audited tables`() {
        val tableNames = AuditLogTableRegistry.tables.map { it.tableName }.toSet()
        val knownAudited =
            setOf(
                "allowance",
                "app_user",
                "attendance",
                "branch",
                "branch_day",
                "branch_inventory",
                "client",
                "commission_manual_inclusion",
                "compensation",
                "concern",
                "credential_token",
                "expense",
                "grant_relief_access",
                "inventory_movement",
                "medical_mission_delegate",
                "product",
                "product_category",
                "product_sale",
                "relief_invite",
                "remittance",
                "remittance_day_breakdown",
                "remittance_financial_snapshot",
                "remittance_line",
                "session",
                "session_base_rate",
                "session_concern",
                "session_practitioner",
                "session_void",
                "user_branch_assignment",
                "user_role",
            )
        assertEquals(knownAudited, tableNames)
    }
}
