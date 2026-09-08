package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Semantic-ownership classification fixture pins (map #615 #634): pure PSI proofs for
 * resurrected/shared/target-layout ownership, table declaration, projection helpers,
 * allowlist and authorization-leak gates in [SemanticOwnershipArchitectureTest].
 *
 * Lives here instead of `SemanticOwnershipArchitectureTest` so that class stays
 * under its `LargeClass` pin (same precedent as `ProjectionArchitectureTest`
 * and `WorkforceReadsArchitectureTest`).
 */
class SemanticOwnershipClassificationFixtureTest {
    private fun parse(source: String): KtFile = BackendArchitectureOwners.parseKt(source)

    private fun isForeignTableWrite(
        op: String,
        importer: String?,
        tableOwners: Map<String, String>,
    ): Boolean {
        val table = op.substringBefore('.')
        val tableOwner = tableOwners[table] ?: return false
        return tableOwner != importer
    }

    @Test
    fun `fixture - resurrected shared store grants no ownership standing`() {
        val widgetStore =
            """
            package com.companyb.companyapp.repository

            internal object WidgetRepository {
                fun findInTransaction() = 1
            }
            """.trimIndent()
        val importer =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.repository.WidgetRepository

            internal object SessionService {
                fun read() = WidgetRepository.findInTransaction()
            }
            """.trimIndent()
        val tree =
            mapOf(
                "repository/WidgetRepository.kt" to parse(widgetStore),
                "session/WidgetUser.kt" to parse(importer),
            )
        assertNull(BackendArchitectureOwners.ownerOf("repository/WidgetRepository.kt"))
        assertTrue(
            BackendArchitectureOwners.treeStoreOwners(tree).isEmpty(),
            "an unclassified path must contribute no store ownership",
        )
        val unclassified = tree.keys.filter { BackendArchitectureOwners.ownerOf(it) == null }
        assertEquals(
            listOf("repository/WidgetRepository.kt"),
            unclassified,
            "a used shared resurrection must still fail the whole-tree classification gate",
        )
        // Unknown stores stay silent in dependency enforcement by design — the
        // classification gate above is the enforcement for resurrected paths (#607).
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignStoreRefs(
                "session",
                parse(importer),
                BackendArchitectureOwners.treeStoreOwners(tree),
            ),
        )
    }

    @Test
    fun `fixture - resurrected shared table stays outside table ownership`() {
        val widgetTable =
            """
            package com.companyb.companyapp.repository.model

            object WidgetTable
            """.trimIndent()
        val writer =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.repository.model.WidgetTable

            internal object WidgetRepository {
                fun write() = WidgetTable.insert { }
            }
            """.trimIndent()
        val tree =
            mapOf(
                "repository/model/WidgetTable.kt" to parse(widgetTable),
                "session/WidgetWriter.kt" to parse(writer),
            )
        assertNull(BackendArchitectureOwners.ownerOf("repository/model/WidgetTable.kt"))
        assertTrue(
            BackendArchitectureOwners.treeTableNames(tree) == setOf("WidgetTable"),
            "table names still derive mechanically even where the owner does not",
        )
        val unclassified = tree.keys.filter { BackendArchitectureOwners.ownerOf(it) == null }
        assertEquals(
            listOf("repository/model/WidgetTable.kt"),
            unclassified,
            "a written shared resurrection must still fail the whole-tree classification gate",
        )
        assertEquals(
            listOf("WidgetTable.insert"),
            BackendArchitectureOwners.tableWriteOps(parse(writer), setOf("WidgetTable")),
            "the foreign write stays mechanically visible — no exemption swallows it",
        )
    }

    @Test
    fun `fixture - target-layout store counterpart keeps its standing`() {
        val widgetStore =
            """
            package com.companyb.companyapp.commerce

            internal object WidgetStore {
                fun findInTransaction() = 1
            }
            """.trimIndent()
        val tree = mapOf("commerce/WidgetStore.kt" to parse(widgetStore))
        assertEquals("commerce", BackendArchitectureOwners.ownerOf("commerce/WidgetStore.kt"))
        assertEquals(
            mapOf("WidgetStore" to setOf("commerce")),
            BackendArchitectureOwners.treeStoreOwners(tree),
        )
        val sameOwner =
            """
            package com.companyb.companyapp.commerce

            internal object WidgetService {
                fun read() = WidgetStore.findInTransaction()
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignStoreRefs(
                "commerce",
                parse(sameOwner),
                mapOf("WidgetStore" to setOf("commerce")),
                emptySet(),
            ),
        )
        val foreignImporter =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.commerce.WidgetStore

            internal object SessionService {
                fun read() = WidgetStore.findInTransaction()
            }
            """.trimIndent()
        assertEquals(
            listOf("import com.companyb.companyapp.commerce.WidgetStore"),
            BackendArchitectureOwners.foreignStoreRefs(
                "session",
                parse(foreignImporter),
                mapOf("WidgetStore" to setOf("commerce")),
                emptySet(),
            ),
        )
    }

    @Test
    fun `fixture - target-layout table counterpart keeps its standing`() {
        val widgetTable =
            """
            package com.companyb.companyapp.commerce

            object WidgetTable
            """.trimIndent()
        val tableTree = mapOf("commerce/WidgetTable.kt" to parse(widgetTable))
        assertEquals("commerce", BackendArchitectureOwners.ownerOf("commerce/WidgetTable.kt"))
        assertTrue(
            "WidgetTable" in BackendArchitectureOwners.treeTableNames(tableTree),
            "a feature-colocated table keeps its mechanical standing",
        )
        val tableOwners = mapOf("WidgetTable" to "commerce")
        assertTrue(isForeignTableWrite("WidgetTable.insert", "session", tableOwners))
        assertTrue(!isForeignTableWrite("WidgetTable.insert", "commerce", tableOwners))
    }

    @Test
    fun `fixture - mechanism-owned table fails the declaration gate`() {
        val facade =
            """
            package com.companyb.companyapp.utils

            object SharedTable

            object Persistence {
                fun write() = SharedTable.insert { }
            }
            """.trimIndent()
        val tree = mapOf("utils/Persistence.kt" to parse(facade))
        assertEquals("mechanism", BackendArchitectureOwners.ownerOf("utils/Persistence.kt"))
        val offenders =
            tree.flatMap { (path, file) ->
                BackendArchitectureOwners.declaredTableNames(file).map { "$it in $path" }
            }
        assertEquals(listOf("SharedTable in utils/Persistence.kt"), offenders)
    }

    @Test
    fun `fixture - projections private helpers comments and strings behave`() {
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.nonInternalStoreDeclarations(
                parse("package f\ninternal object FooRepository {}\nprivate object BarStore {}\n"),
            ),
        )
        assertEquals(
            listOf("object BazRepository", "class QuxStore"),
            BackendArchitectureOwners.nonInternalStoreDeclarations(
                parse("package f\nobject BazRepository {}\nclass QuxStore {}\n"),
            ),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.apiLayerViolations(
                parse("package f\n// transaction { exec(\"x\") }\nobject Clean\n"),
            ),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.tableLeaks(
                parse(
                    """
                    package fixture
                    import com.companyb.companyapp.commerce.WidgetTable

                    object Svc {
                        fun go(): String = "WidgetTable.selectAll()"
                    }
                    // WidgetTable in prose must not count
                    /* WidgetTable in a block must not count */
                    internal object Seam {
                        val t = WidgetTable.tableName
                    }
                    """.trimIndent(),
                ),
                emptySet(),
            ),
        )
    }

    @Test
    fun `fixture - empty allowlist stays legal with no dummy entry`() {
        val sameOwner =
            """
            package com.companyb.companyapp.client
            import com.companyb.companyapp.client.ClientRepository

            internal object ClientService {
                fun read() = ClientRepository.findByIdInTransaction("x")
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignStoreRefs(
                "client",
                parse(sameOwner),
                mapOf("ClientRepository" to setOf("client")),
                emptySet(),
            ),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.tableWriteOps(parse("package f\nfun empty() = 1\n"), emptySet()),
        )
        val write =
            """
            package com.companyb.companyapp.client
            import com.companyb.companyapp.client.ClientTable

            internal object ClientRepository {
                fun write() = ClientTable.insert { }
            }
            """.trimIndent()
        assertEquals(
            listOf("ClientTable.insert"),
            BackendArchitectureOwners.tableWriteOps(parse(write), setOf("ClientTable")),
        )
    }

    @Test
    fun `fixture - authorization feature leak rule flags exact symbols only`() {
        val dirty =
            """
            package com.companyb.companyapp.authorization
            import com.companyb.companyapp.session.SessionReads
            import com.companyb.companyapp.contracts.session.isStatusCorrection

            object CapabilityFilter
            """.trimIndent()
        assertEquals(
            listOf(
                "import com.companyb.companyapp.session.SessionReads",
                "import com.companyb.companyapp.contracts.session.isStatusCorrection",
            ),
            BackendArchitectureOwners.authorizationFeatureLeaks(parse(dirty)),
        )
        val wildcardEvasion =
            """
            package com.companyb.companyapp.authorization
            import com.companyb.companyapp.session.*

            object CapabilityFilter {
                fun gate() = SessionReads.findById(id)
            }
            """.trimIndent()
        assertEquals(
            listOf("use SessionReads"),
            BackendArchitectureOwners.authorizationFeatureLeaks(parse(wildcardEvasion)),
        )
        val qualifiedEvasion =
            """
            package com.companyb.companyapp.authorization

            object CapabilityFilter {
                fun gate() = com.companyb.companyapp.finance.FinanceReads.findExpenseById(id)
            }
            """.trimIndent()
        assertEquals(
            listOf("use FinanceReads"),
            BackendArchitectureOwners.authorizationFeatureLeaks(parse(qualifiedEvasion)),
        )
        val clean =
            """
            package com.companyb.companyapp.authorization
            import com.companyb.companyapp.branchday.BranchDayService
            import com.companyb.companyapp.contracts.authorization.CapabilityCodes

            object CapabilityFilter
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.authorizationFeatureLeaks(parse(clean)),
        )
    }
}
