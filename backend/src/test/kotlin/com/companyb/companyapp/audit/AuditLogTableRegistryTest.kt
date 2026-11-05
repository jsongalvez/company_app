package com.companyb.companyapp.audit

import java.io.File
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
    fun `every table a write-site audits is in the registry`() {
        val sources =
            File("backend/src/main/kotlin")
                .walk()
                .filter { it.extension == "kt" }
                .joinToString("\n") { it.readText() }
        assertTrue(sources.isNotEmpty(), "no backend sources found")
        val tableOf = names(TABLE_OBJECT, sources) + names(STRING_CONST, sources)
        val audited =
            AUDITED_ROW
                .findAll(sources)
                .map { it.groupValues[1] }
                .filterNot { it == "val" }
                .toSet()
        assertTrue(audited.size > 10, "found only $audited")
        val registered = AuditLogTableRegistry.tables.map { it.tableName }.toSet()
        val missing =
            audited
                .map { tableOf[it] ?: "unresolved $it" }
                .filterNot { it in registered }
                .sorted()
        // visit_booking is audited but not listed yet; listing it changes the Audit Log table list.
        assertEquals(listOf("visit_booking"), missing, "audited at a write-site but missing from AuditLogTableRegistry")
    }

    private fun names(
        pattern: Regex,
        sources: String,
    ): Map<String, String> = pattern.findAll(sources).associate { it.groupValues[1] to it.groupValues[2] }

    private companion object {
        val TABLE_OBJECT = Regex("""object (\w+)\s*:\s*Table\("([a-z_]+)"\)""")
        val STRING_CONST = Regex("""const val (\w+) = "([a-z_]+)"""")
        val AUDITED_ROW = Regex("""AuditedRow\(\s*(\w+)\b""")
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
