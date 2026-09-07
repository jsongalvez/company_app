package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared contract guard (#459): [CapabilityCodes] is the single runtime owner.
 * Migration SQL keeps its database-owned literals (history is never rewritten);
 * tests and seeders may hold raw strings. This check fails when either side
 * drifts from the canonical set.
 */
class CapabilityCodesConsistencyTest {
    private val canonical: Set<String> =
        CapabilityCodes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.get(null) as String }
            .toSet()

    @Test
    fun `canonical set holds the eleven contract codes`() {
        assertEquals(
            setOf(
                "VIEW_BRANCH_DATA",
                "EDIT_BRANCH_DATA",
                "EDIT_PAST_DAY",
                "VOID_SESSION",
                "SUBMIT_REMITTANCE",
                "ASSIGN_COMPENSATION",
                "MANAGE_PRODUCTS",
                "MANAGE_CATALOG",
                "MANAGE_USERS",
                "ASSIGN_DELEGATE",
                "RECEIVE_NEXT_APPOINTMENT_ALERTS",
            ),
            canonical,
        )
    }

    @Test
    fun `migration capability literals match the canonical set`() {
        val migrationDir = File("backend/src/main/resources/db/migration")
        assertTrue(migrationDir.isDirectory, "migration dir missing: ${migrationDir.path}")
        val quoted = Regex("'([A-Z][A-Z_]*)'")
        val allQuoted =
            migrationDir
                .listFiles { file -> file.extension == "sql" }!!
                .flatMap { quoted.findAll(it.readText()).map { match -> match.groupValues[1] } }
                .toSet()
        // Suffix heuristic for the unknown-direction only; the seeded-direction checks the
        // full literal set so DAY/SESSION-suffixed codes are covered without enum collisions.
        val capabilityish =
            Regex(".*(DATA|USERS|DELEGATE|REMITTANCE|COMPENSATION|PRODUCTS|CATALOG|ALERTS|PAST_DAY|VOID_SESSION)$")
        val mentioned =
            allQuoted
                .filter { capabilityish.matches(it) }
                .filterNot { it == "MEDICAL_MISSION_DELEGATE" }
                .toSet()
        assertTrue(mentioned.isNotEmpty(), "no capability literals found in migrations")
        val unknown = mentioned - canonical
        assertTrue(unknown.isEmpty(), "migration capability codes missing from CapabilityCodes: $unknown")
        val unseeded = canonical - allQuoted
        assertTrue(unseeded.isEmpty(), "canonical codes absent from migrations: $unseeded")
    }

    @Test
    fun `test and seeder raw capability strings match the canonical set`() {
        val roots =
            listOf(
                File("backend/src/test/kotlin"),
                File("backend/src/main/kotlin"),
            ).filter { it.isDirectory }
        assertTrue(roots.isNotEmpty(), "no Kotlin source roots found")
        val literal = Regex("\"([A-Z][A-Z_]{3,})\"")
        val capabilityish =
            Regex(".*(DATA|USERS|DELEGATE|REMITTANCE|COMPENSATION|PRODUCTS|CATALOG|ALERTS|PAST_DAY|VOID_SESSION)$")
        val raw =
            roots
                .flatMap { root ->
                    root
                        .walkTopDown()
                        .filter { it.extension == "kt" }
                        .flatMap { literal.findAll(it.readText()).map { match -> match.groupValues[1] } }
                }.filter { capabilityish.matches(it) }
                .filterNot { it == "MEDICAL_MISSION_DELEGATE" }
                .toSet()
        val unknown = raw - canonical
        assertTrue(unknown.isEmpty(), "raw capability strings missing from CapabilityCodes: $unknown")
    }
}
