package com.companyb.companyapp.architecture

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Semantic-owner architecture checks (#534, map #533).
 *
 * Stronger replacements for the folder-pinned predecessors, kept alongside
 * them: BackendFeatureBoundary (HTTP adapter, seam hiding, store visibility,
 * audit ownership), Crud/Attendance/Expense/Remittance ownership pins
 * (command-owned transactions without file-count pinning), TimeOwnership
 * (clock owners). Each rule below names the check it replaces.
 *
 * Fixtures prove branch-day ownership in legacy (`service/branchday`) and
 * target (`branchday`) packages; production movement belongs to #536/#537.
 */
class SemanticOwnershipArchitectureTest {
    private val sources: Map<String, String> by lazy { BackendArchitectureOwners.discover() }

    private fun isServiceScope(path: String): Boolean =
        path.startsWith("service/") ||
            TARGET_FEATURE_DIRS.any { path.startsWith(it) }

    // ---- Discovery (replaces api/service/repository-only scans) ----

    @Test
    fun `discovery covers the whole backend tree and fails closed`() {
        assertTrue(sources.isNotEmpty(), "whole-source discovery must find the backend tree")
        val empty = Files.createTempDirectory("empty-arch-discovery").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { BackendArchitectureOwners.discover(empty) }
        } finally {
            empty.deleteRecursively()
        }
        assertFailsWith<IllegalArgumentException> {
            BackendArchitectureOwners.discover(File("backend/src/test/fixtures/does-not-exist-534"))
        }
    }

    @Test
    fun `every production file has an explicit owner in legacy or target packages`() {
        val unclassified = sources.keys.filter { BackendArchitectureOwners.ownerOf(it) == null }
        assertTrue(unclassified.isEmpty(), "unclassified production packages:\n${unclassified.joinToString("\n")}")
    }

    @Test
    fun `owner mapping covers legacy and target branch-day packages so deletion cannot pass silently`() {
        assertEquals("branchday", BackendArchitectureOwners.ownerOf("service/branchday/BranchDayService.kt"))
        assertEquals("branchday", BackendArchitectureOwners.ownerOf("branchday/BranchDayService.kt"))
        assertEquals("branch", BackendArchitectureOwners.ownerOf("service/BranchService.kt"))
        assertEquals("branch", BackendArchitectureOwners.ownerOf("branch/BranchService.kt"))
        assertEquals("identity", BackendArchitectureOwners.ownerOf("service/AuthService.kt"))
        assertEquals("identity", BackendArchitectureOwners.ownerOf("identity/AuthService.kt"))
        assertEquals("http", BackendArchitectureOwners.ownerOf("api/routes/BranchDayRoutes.kt"))
        assertEquals("persistence", BackendArchitectureOwners.ownerOf("repository/BranchRepository.kt"))
    }

    // ---- HTTP adapter (replaces BackendFeatureBoundary api scan) ----

    @Test
    fun `http owner stays an adapter with no persistence shapes`() {
        val offenders =
            sources
                .filter { (path, _) -> BackendArchitectureOwners.ownerOf(path) == "http" }
                .flatMap { (path, source) ->
                    BackendArchitectureOwners.apiLayerViolations(source).map { "$path: $it" }
                }
        assertTrue(offenders.isEmpty(), "http owner must not touch persistence:\n${offenders.joinToString("\n")}")
    }

    // ---- Seam hiding (replaces BackendFeatureBoundary table-leak scan) ----

    @Test
    fun `feature surfaces hide persistence tables behind internal seams`() {
        val offenders =
            sources
                .filter { (path, _) -> isServiceScope(path) }
                .flatMap { (path, source) ->
                    BackendArchitectureOwners.tableLeaks(source).map { "$path: $it" }
                }
        assertTrue(offenders.isEmpty(), "public surfaces must not touch tables:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `feature-local stores stay internal`() {
        val offenders =
            sources
                .filter { (path, _) -> isServiceScope(path) }
                .flatMap { (path, source) ->
                    BackendArchitectureOwners.nonInternalStoreDeclarations(source).map { "$path: $it" }
                }
        assertTrue(offenders.isEmpty(), "stores must be internal:\n${offenders.joinToString("\n")}")
    }

    // ---- Audit ownership (replaces BackendFeatureBoundary audit scan) ----

    @Test
    fun `persistence never owns audit writes and auditFn stays retired everywhere`() {
        val recordCalls =
            sources
                .filter { (path, _) -> BackendArchitectureOwners.ownerOf(path) == "persistence" }
                .filter { (_, source) -> BackendArchitectureOwners.containsAuditRecordCall(source) }
                .keys
                .toList()
        assertTrue(
            recordCalls.isEmpty(),
            "audit writes belong to feature seams/commands:\n${recordCalls.joinToString("\n")}",
        )
        val auditFnFiles =
            sources.filter { (_, source) -> BackendArchitectureOwners.containsAuditFn(source) }.keys.toList()
        assertTrue(auditFnFiles.isEmpty(), "auditFn callback must stay retired:\n${auditFnFiles.joinToString("\n")}")
    }

    // ---- Command transactions (replaces per-file transaction-count pins) ----

    @Test
    fun `each command owns at most one transaction without pinning file counts`() {
        val offenders =
            sources
                .mapValues { (_, source) -> BackendArchitectureOwners.maxTransactionsPerFunction(source) }
                .filter { (_, max) -> max > 1 }
                .map { (path, max) -> "$path: $max transactions in one function" }
        assertTrue(offenders.isEmpty(), "commands must own at most one transaction:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `in-transaction stores open no transaction of their own`() {
        val offenders =
            sources.flatMap { (path, source) ->
                BackendArchitectureOwners.inTransactionFunctionsWithNestedTransaction(source).map { "$path: $it" }
            }
        assertTrue(offenders.isEmpty(), "stores must not nest transactions:\n${offenders.joinToString("\n")}")
    }

    // ---- Branch-day proof owner (fixtures only; production moves in #536) ----

    @Test
    fun `branch-day tables are written only by the branchday owner`() {
        val offenders =
            sources
                .filter { (path, _) -> BackendArchitectureOwners.ownerOf(path) != "branchday" }
                .flatMap { (path, source) ->
                    BackendArchitectureOwners.branchDayTableWrites(source).map { "$path: BranchDayTable.$it" }
                }
        assertTrue(
            offenders.isEmpty(),
            "branch-day writes belong to the branchday owner:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `direct branch-day store readers are explicitly recorded and consumed`() {
        val readers = BackendArchitectureOwners.branchDayStoreReaders
        assertTrue(readers.isNotEmpty(), "exception list must not be deletable into silent pass")
        for (path in readers) {
            val source = sources[path] ?: error("recorded reader missing from tree: $path")
            assertTrue(
                BackendArchitectureOwners.importsBranchDayStore(source),
                "recorded reader no longer uses the store (remove it): $path",
            )
        }
        val unrecorded =
            sources
                .filter { (path, _) -> path !in readers && BackendArchitectureOwners.ownerOf(path) != "branchday" }
                .filter { (_, source) -> BackendArchitectureOwners.importsBranchDayStore(source) }
                .keys
                .toList()
        assertTrue(unrecorded.isEmpty(), "unrecorded branch-day store readers:\n${unrecorded.joinToString("\n")}")
    }

    // ---- Clock ownership (replaces TimeOwnership path allowlists) ----

    @Test
    fun `manila zone is constructed only inside the branchday owner`() {
        val sites = sources.filter { (_, source) -> BackendArchitectureOwners.containsManilaZone(source) }.keys.toList()
        assertEquals(1, sites.size, "exactly one ZoneId site must exist: $sites")
        assertEquals("branchday", BackendArchitectureOwners.ownerOf(sites.single()))
        assertTrue(
            "Asia/Manila" in sources.getValue(sites.single()),
            "the sole ZoneId site must construct the Manila zone",
        )
    }

    @Test
    fun `no consumer derives a calendar today independently`() {
        val offenders =
            sources.filter { (_, source) -> BackendArchitectureOwners.containsIndependentToday(source) }.keys.toList()
        assertTrue(offenders.isEmpty(), "LocalDate.now/OffsetDateTime.now must not appear: $offenders")
    }

    @Test
    fun `instant reads stay limited to recorded owners and each record is consumed`() {
        val owners = BackendArchitectureOwners.instantOwners
        for (path in owners) {
            val source = sources[path] ?: error("recorded clock owner missing from tree: $path")
            assertTrue(
                BackendArchitectureOwners.containsInstantNow(source),
                "recorded clock owner no longer reads Instant (remove it): $path",
            )
        }
        val offenders =
            sources
                .filter { (path, _) -> path !in owners }
                .filter { (_, source) -> BackendArchitectureOwners.containsInstantNow(source) }
                .keys
                .toList()
        assertTrue(offenders.isEmpty(), "unrecorded Instant.now() sites: $offenders")
    }

    @Test
    fun `scheduler reads the operational day through the branch-day boundary`() {
        val scheduler = sources["service/NextAppointmentScheduler.kt"] ?: error("scheduler missing from tree")
        assertTrue(scheduler.contains("ZonedDateTime.now(clock)"), "scheduler reads only its injected clock")
        assertTrue(
            scheduler.contains("BranchDayService.currentOperationalDate("),
            "run date comes from the operational-day authority",
        )
        assertTrue(!scheduler.contains(".toLocalDate()"), "scheduler must not derive a calendar date itself")
    }

    // ---- Fixtures: legacy and target packages, aliases, comments ----

    @Test
    fun `fixture - post-seam leaks fail in legacy and target packages`() {
        val legacyLeak =
            """
            package fixture
            import com.companyb.companyapp.repository.model.WidgetTable

            internal object Seam {
                val t = 1
            }

            object Later {
                fun r() = WidgetTable.selectAll()
            }
            """.trimIndent()
        assertEquals(listOf("WidgetTable"), BackendArchitectureOwners.tableLeaks(legacyLeak))

        val targetLeak =
            """
            package com.companyb.companyapp.branchday
            import com.companyb.companyapp.repository.model.BranchDayTable

            object BranchDayService {
                fun r() = BranchDayTable.selectAll()
            }

            internal object Seam {
                val t = 1
            }
            """.trimIndent()
        assertEquals(listOf("BranchDayTable"), BackendArchitectureOwners.tableLeaks(targetLeak))

        val targetSeam =
            """
            package com.companyb.companyapp.branchday
            import com.companyb.companyapp.repository.model.BranchDayTable

            internal object BranchDayAudit {
                val t = BranchDayTable.tableName
            }
            """.trimIndent()
        assertEquals(emptyList(), BackendArchitectureOwners.tableLeaks(targetSeam))
    }

    @Test
    fun `fixture - import aliases and comments never decide violations`() {
        val aliasLeak =
            """
            package fixture
            import com.companyb.companyapp.repository.model.BranchDayTable as DayT

            object Svc {
                fun r() = DayT.selectAll()
            }

            internal object Seam {
                val t = 1
            }
            """.trimIndent()
        assertEquals(listOf("BranchDayTable"), BackendArchitectureOwners.tableLeaks(aliasLeak))

        val commentBlind =
            """
            package fixture
            import com.companyb.companyapp.repository.model.WidgetTable

            object Svc {
                fun go(): Int = 1
            }
            // WidgetTable in prose must not count
            /* WidgetTable in a block must not count */
            internal object Seam {
                val t = WidgetTable.tableName
            }
            """.trimIndent()
        assertEquals(emptyList(), BackendArchitectureOwners.tableLeaks(commentBlind))
        assertEquals(emptyList(), BackendArchitectureOwners.apiLayerViolations("// transaction { exec(\"x\") }"))
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.nonInternalStoreDeclarations("// object GhostRepository"),
        )
    }

    @Test
    fun `fixture - forbidden store imports fail in legacy and target packages`() {
        val legacy =
            "import com.companyb.companyapp.service.branchday.BranchDayRepository\n" +
                "object Svc { fun r() = 1 }\n"
        assertTrue(BackendArchitectureOwners.importsBranchDayStore(legacy))
        val target =
            "import com.companyb.companyapp.branchday.BranchDayRepository\n" +
                "object Svc { fun r() = 1 }\n"
        assertTrue(BackendArchitectureOwners.importsBranchDayStore(target))
        assertTrue(!BackendArchitectureOwners.importsBranchDayStore("object Svc { fun r() = 1 }\n"))
    }

    private companion object {
        val TARGET_FEATURE_DIRS =
            listOf(
                "branch/",
                "branchday/",
                "identity/",
                "authorization/",
                "workforce/",
                "client/",
                "session/",
                "commerce/",
                "commission/",
                "remittance/",
                "reporting/",
                "audit/",
                "notification/",
                "http/",
            )
    }
}
