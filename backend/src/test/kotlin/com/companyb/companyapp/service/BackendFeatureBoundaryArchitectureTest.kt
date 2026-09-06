package com.companyb.companyapp.service

import com.companyb.companyapp.architecture.BackendArchitectureOwners
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #324 executable backend feature boundaries — whole-backend scans that generalize the
 * per-batch pins of CrudCommandOwnershipArchitectureTest:
 *
 * Predicates live in [BackendArchitectureOwners] (#534); this test keeps the
 * boundary names and fixtures while the owner carries the declaration-aware,
 * alias-aware, comment-blind scanners.
 *
 * 1. The api layer stays an HTTP adapter: no Exposed imports, no transaction blocks,
 *    no persistence-table imports, no raw-SQL exec.
 * 2. Persistence-table knowledge in the service layer lives behind internal objects
 *    (`*Audit` seams and `*Repository` stores); public command/service surfaces never touch it.
 * 3. Feature-local stores in the service layer are declared `internal`.
 * 4. Persistence never owns audit writes (`AuditLogRepository.record*` calls stay in feature
 *    seams/commands) and the retired `auditFn` callback stays gone.
 *
 * Fixtures below pin allowed and forbidden shapes for every rule.
 */
class BackendFeatureBoundaryArchitectureTest {
    private val mainRoot = File("backend/src/main/kotlin/com/companyb/companyapp")

    // ---- Rules (delegate to the semantic owner; fixtures below) ----

    fun apiLayerViolations(source: String): List<String> = BackendArchitectureOwners.apiLayerViolations(source)

    /** Persistence-table identifiers (imported from repository.model) on the public surface of a service-layer file. */
    fun tableLeaks(source: String): List<String> = BackendArchitectureOwners.tableLeaks(source)

    /** Top-level store declarations missing the `internal` visibility marker. */
    fun nonInternalStoreDeclarations(source: String): List<String> =
        BackendArchitectureOwners.nonInternalStoreDeclarations(source)

    // ---- Whole-backend scans ----

    @Test
    fun `api layer stays an http adapter`() {
        val routes =
            listOf(File(mainRoot, "branch"), File(mainRoot, "branchday"))
                .flatMap { sources(it).toList() }
                .filter { (file, _) -> file.endsWith("Routes.kt") }
                .toMap()
        val offenders =
            (sources(File(mainRoot, "api")) + routes).flatMap { (file, source) ->
                apiLayerViolations(source).map { rule -> "$file: $rule" }
            }
        assertTrue(offenders.isEmpty(), "api layer must not touch persistence:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `service layer hides persistence-table knowledge behind internal objects`() {
        val roots =
            listOf(File(mainRoot, "service"), File(mainRoot, "branch"), File(mainRoot, "branchday"))
        val offenders =
            roots.flatMap { sources(it).toList() }.flatMap { (file, source) ->
                tableLeaks(source).map { token -> "$file: $token" }
            }
        assertTrue(offenders.isEmpty(), "public surfaces must not touch tables:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `feature-local stores are internal`() {
        val roots =
            listOf(File(mainRoot, "service"), File(mainRoot, "branch"), File(mainRoot, "branchday"))
        val offenders =
            roots.flatMap { sources(it).toList() }.flatMap { (file, source) ->
                nonInternalStoreDeclarations(source).map { decl -> "$file: $decl" }
            }
        assertTrue(offenders.isEmpty(), "stores must be internal:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `persistence never owns audit writes and auditFn stays retired`() {
        val recordCalls =
            sources(File(mainRoot, "repository"))
                .filter { (_, source) -> BackendArchitectureOwners.containsAuditRecordCall(source) }
                .keys
                .toList()
        assertTrue(
            recordCalls.isEmpty(),
            "audit writes belong to feature seams/commands, not repositories:\n${recordCalls.joinToString("\n")}",
        )
        val auditFnFiles =
            sources(mainRoot).filter { (_, source) -> BackendArchitectureOwners.containsAuditFn(source) }.keys.toList()
        assertTrue(auditFnFiles.isEmpty(), "auditFn callback must stay retired:\n${auditFnFiles.joinToString("\n")}")
    }

    // ---- Fixtures: allowed and forbidden shapes per rule ----

    @Test
    fun `fixture - table rule accepts seam placement and rejects public leaks`() {
        val allowed =
            """
            package fixture
            import com.companyb.companyapp.repository.model.ThingTable

            object ThingService {
                fun go(): Int = 1
            }

            internal object ThingAudit {
                val t = ThingTable.tableName
            }
            """.trimIndent()
        assertEquals(emptyList(), tableLeaks(allowed))

        val leaked =
            """
            package fixture
            import com.companyb.companyapp.repository.model.ThingTable

            object ThingService {
                fun go(): String = ThingTable.tableName
            }

            internal object ThingAudit {
                val t = ThingTable.tableName
            }
            """.trimIndent()
        assertEquals(listOf("ThingTable"), tableLeaks(leaked))

        // A same-named-looking class that is NOT persistence (e.g. iText PdfPTable) is not a leak.
        val foreignClassIsNotPersistence =
            """
            package fixture

            object PdfService {
                fun r() = PdfPTable(widths)
            }
            """.trimIndent()
        assertEquals(emptyList(), tableLeaks(foreignClassIsNotPersistence))

        val noSeamAtAll =
            """
            package fixture
            import com.companyb.companyapp.repository.model.WidgetTable

            object OtherService {
                fun r() = WidgetTable.selectAll()
            }
            """.trimIndent()
        assertEquals(listOf("WidgetTable"), tableLeaks(noSeamAtAll))
    }

    @Test
    fun `fixture - api rule flags every persistence concern`() {
        assertEquals(
            emptyList(),
            apiLayerViolations(
                """
                package fixture
                object CleanRoutes {
                    fun register() {}
                }
                """.trimIndent(),
            ),
        )
        val violations =
            apiLayerViolations(
                """
                package fixture
                import org.jetbrains.exposed.v1.jdbc.transactions.transaction
                import com.companyb.companyapp.repository.model.WidgetTable

                object DirtyRoutes {
                    fun handler() {
                        transaction { exec("SELECT 1") }
                    }
                }
                """.trimIndent(),
            )
        assertEquals(
            listOf("exposed-import", "transaction-block", "persistence-table-import", "raw-sql-exec"),
            violations,
        )
    }

    @Test
    fun `fixture - store visibility requires internal`() {
        assertEquals(
            emptyList(),
            nonInternalStoreDeclarations("internal object FooRepository {\n}\n\ninternal class BarStore\n"),
        )
        assertEquals(
            listOf("object BazRepository", "class QuxStore"),
            nonInternalStoreDeclarations("object BazRepository {\n}\n\nclass QuxStore\n"),
        )
    }

    private fun sources(root: File): Map<String, String> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.path to it.readText() }
}
