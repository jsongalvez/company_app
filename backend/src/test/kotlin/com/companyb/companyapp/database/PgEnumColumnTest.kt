package com.companyb.companyapp.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PgEnumColumnTest {
    @Test
    fun `enum columns use pgEnum, not Exposed customEnumeration`() {
        val sources = File("backend/src/main/kotlin")
        assertTrue(sources.isDirectory, "run from the repo root")
        val offenders =
            sources
                .walkTopDown()
                .filter { it.extension == "kt" }
                .filter { file -> file.readLines().any { "customEnumeration<" in it } }
                .map { it.path }
                .toList()
        assertEquals(
            emptyList(),
            offenders,
            "Declare enum columns with database.pgEnum(name, sql, Enum.entries): customEnumeration casts every cell " +
                "and checks it by reflection",
        )
    }
}
