package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Workforce seam pins (map #615 #604): session/commission read membership, slot,
 * and attendance facts through `WorkforceReads` — the retired whole-store grants
 * (`AttendanceRepository`, `UserBranchAssignmentRepository`, `BranchMemberRepository`)
 * stay banned. Lives here instead of `SemanticOwnershipArchitectureTest` so that
 * class stays under its `LargeClass` pin.
 */
class WorkforceReadsArchitectureTest {
    private fun parse(source: String): KtFile = BackendArchitectureOwners.parseKt(source)

    @Test
    fun `fixture - retired workforce store grants stay banned for session and commission`() {
        val stores =
            mapOf(
                "AttendanceRepository" to setOf("workforce"),
                "UserBranchAssignmentRepository" to setOf("workforce"),
                "BranchMemberRepository" to setOf("workforce"),
            )
        val cases =
            listOf(
                "session" to "UserBranchAssignmentRepository",
                "session" to "BranchMemberRepository",
                "commission" to "AttendanceRepository",
            )
        for ((importerOwner, store) in cases) {
            val importer =
                """
                package com.companyb.companyapp.$importerOwner
                import com.companyb.companyapp.workforce.$store

                internal object Consumer {
                    fun read() = $store.read()
                }
                """.trimIndent()
            assertEquals(
                listOf("import com.companyb.companyapp.workforce.$store"),
                BackendArchitectureOwners.foreignStoreRefs(importerOwner, parse(importer), stores, emptySet()),
            )
        }
    }
}
