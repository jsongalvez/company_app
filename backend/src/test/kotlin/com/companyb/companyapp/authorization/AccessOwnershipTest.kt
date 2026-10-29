package com.companyb.companyapp.authorization

import com.companyb.companyapp.architecture.BackendArchitectureOwners
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every permission check goes through [Access] and names an [Act], so `Act` stays the one list of
 * who may do what. Code outside `authorization/` may not ask the grant store directly.
 */
class AccessOwnershipTest {
    @Test
    fun `only the authorization package reads grants to decide access`() {
        val banned = Regex("""CapabilityRepository\.|CapabilityService\.has\w*\(""")
        val sources =
            BackendArchitectureOwners.mainRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        assertTrue(
            sources.size > 100,
            "scanned ${sources.size} main sources from ${BackendArchitectureOwners.mainRoot.absolutePath}",
        )
        val offenders =
            sources
                .filter { it.parentFile.name != "authorization" }
                .flatMap { file ->
                    file.readLines().withIndex().filter { banned.containsMatchIn(it.value) }.map { (i, line) ->
                        "${file.relativeTo(
                            BackendArchitectureOwners.mainRoot,
                        )}:${i + 1}: ${line.trim()} (use Access.allows/require with an Act)"
                    }
                }
        assertEquals(emptyList(), offenders)
    }
}
