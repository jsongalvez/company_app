package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Semantic-ownership seam fixture pins (map #615 #634): pure PSI proofs for seam hiding,
 * audit, store, table-leak and transaction-shape gates in
 * [SemanticOwnershipArchitectureTest].
 *
 * Lives here instead of `SemanticOwnershipArchitectureTest` so that class stays
 * under its `LargeClass` pin (same precedent as `ProjectionArchitectureTest`
 * and `WorkforceReadsArchitectureTest`).
 */
class SemanticOwnershipSeamFixtureTest {
    private fun parse(source: String): KtFile = BackendArchitectureOwners.parseKt(source)

    @Test
    fun `fixture - http persistence access fails in feature routes`() {
        val dirty =
            """
            package fixture
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            import com.companyb.companyapp.commerce.WidgetTable

            object DirtyRoutes {
                fun handler() {
                    transaction { exec("SELECT 1") }
                }
            }
            """.trimIndent()
        val violations = BackendArchitectureOwners.apiLayerViolations(parse(dirty))
        assertEquals(
            listOf("exposed-import", "transaction-block", "persistence-table-import", "raw-sql-exec"),
            violations,
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.apiLayerViolations(
                parse("package fixture\nobject CleanRoutes {\n fun register() {}\n}\n"),
            ),
        )
    }

    @Test
    fun `fixture - relocated store writing an audit row fails`() {
        val store =
            """
            package com.companyb.companyapp.client
            import com.companyb.companyapp.audit.AuditLog

            internal object ClientRepository {
                fun createInTransaction() {
                    AuditLog.recordInsert("client", "1", "caller", mapOf("id" to "1"))
                }
            }
            """.trimIndent()
        val file = parse(store)
        assertEquals("store", BackendArchitectureOwners.roleOf("client/ClientRepository.kt"))
        assertTrue(BackendArchitectureOwners.containsAuditRecordCall(file))
        assertTrue(BackendArchitectureOwners.containsAuditFn(parse("package f\nval auditFn = 1\n")))
        assertTrue(!BackendArchitectureOwners.containsAuditFn(parse("package f\n// auditFn retired\nobject S\n")))
    }

    @Test
    fun `fixture - foreign store import fails even when both sides are internal`() {
        val importer =
            """
            package com.companyb.companyapp.client
            import com.companyb.companyapp.branchday.BranchDayRepository

            internal object ClientService {
                fun read() = BranchDayRepository.findByIdInTransaction("x")
            }
            """.trimIndent()
        val stores = mapOf("BranchDayRepository" to setOf("branchday"))
        val refs = BackendArchitectureOwners.foreignStoreRefs("client", parse(importer), stores, emptySet())
        assertEquals(listOf("import com.companyb.companyapp.branchday.BranchDayRepository"), refs)
        val granted =
            BackendArchitectureOwners.foreignStoreRefs(
                "client",
                parse(importer),
                stores,
                setOf(
                    BackendArchitectureOwners.StoreSeam(
                        "client",
                        "com.companyb.companyapp.branchday.BranchDayRepository",
                    ),
                ),
            )
        assertEquals(emptyList(), granted)
    }

    @Test
    fun `fixture - moved aliased and qualified tables resolve`() {
        val aliasLeak =
            """
            package fixture
            import com.companyb.companyapp.branchday.BranchDayTable as DayT

            object Svc {
                fun r() = DayT.selectAll()
            }

            internal object Seam {
                val t = 1
            }
            """.trimIndent()
        assertEquals(listOf("BranchDayTable"), BackendArchitectureOwners.tableLeaks(parse(aliasLeak), emptySet()))

        val qualifiedLeak =
            """
            package fixture

            object Svc {
                fun r() = com.companyb.companyapp.client.ClientTable.selectAll()
            }
            """.trimIndent()
        assertEquals(listOf("ClientTable"), BackendArchitectureOwners.tableLeaks(parse(qualifiedLeak), emptySet()))

        val samePackageLeak =
            """
            package com.companyb.companyapp.branchday

            object BranchDayService {
                fun r() = BranchDayTable.selectAll()
            }

            internal object Seam {
                val t = 1
            }
            """.trimIndent()
        assertEquals(
            listOf("BranchDayTable"),
            BackendArchitectureOwners.tableLeaks(parse(samePackageLeak), setOf("BranchDayTable")),
        )
        val foreignClassIsNotATable =
            """
            package fixture

            object PdfService {
                fun r() = PdfPTable(widths)
            }
            """.trimIndent()
        assertEquals(emptyList(), BackendArchitectureOwners.tableLeaks(parse(foreignClassIsNotATable), emptySet()))
    }

    @Test
    fun `fixture - post-seam leaks fail regardless of declaration order`() {
        val leakAfterSeam =
            """
            package fixture
            import com.companyb.companyapp.commerce.WidgetTable

            internal object Seam {
                val t = 1
            }

            object Later {
                fun r() = WidgetTable.selectAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("WidgetTable"),
            BackendArchitectureOwners.tableLeaks(parse(leakAfterSeam), emptySet()),
        )
        val seamOnly =
            """
            package fixture
            import com.companyb.companyapp.commerce.WidgetTable

            object ThingService {
                fun go(): Int = 1
            }

            internal object ThingAudit {
                val t = WidgetTable.tableName
            }
            """.trimIndent()
        assertEquals(emptyList(), BackendArchitectureOwners.tableLeaks(parse(seamOnly), emptySet()))
    }

    @Test
    fun `fixture - transaction shapes parse uniformly`() {
        assertEquals(0, BackendArchitectureOwners.maxTransactionsPerFunction(parse("package f\nfun a() = 1\n")))
        val generic =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            fun <T> save(value: T) = transaction { 1 }
            """.trimIndent()
        assertEquals(1, BackendArchitectureOwners.maxTransactionsPerFunction(parse(generic)))
        val extension =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            fun String.store() {
                transaction { length }
            }
            """.trimIndent()
        assertEquals(1, BackendArchitectureOwners.maxTransactionsPerFunction(parse(extension)))
        val nested =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            fun outer() {
                fun inner() {
                    transaction { 1 }
                }
                inner()
            }
            """.trimIndent()
        assertEquals(1, BackendArchitectureOwners.maxTransactionsPerFunction(parse(nested)))
        val annotatedDouble =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            @Suppress("TooManyFunctions")
            fun dirty() {
                transaction { 1 }
                transaction { 2 }
            }
            """.trimIndent()
        assertEquals(2, BackendArchitectureOwners.maxTransactionsPerFunction(parse(annotatedDouble)))
        val nestedInTransactional =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            fun loadInTransaction() {
                transaction { 1 }
            }
            """.trimIndent()
        assertEquals(
            listOf("loadInTransaction"),
            BackendArchitectureOwners.inTransactionFunctionsWithNestedTransaction(parse(nestedInTransactional)),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.inTransactionFunctionsWithNestedTransaction(parse(generic)),
        )
    }

    @Test
    fun `fixture - required command counts ignore comments strings and nested helpers`() {
        val commented =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            object Svc {
                // transaction { this prose must not count }
                /* transaction { block prose } */
                fun save() {
                    val note = "transaction { string lookalike }"
                    transaction { 1 }
                }
            }
            """.trimIndent()
        assertEquals(listOf(1), BackendArchitectureOwners.transactionsInFunction(parse(commented), "save"))
        val nestedHelper =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            object Svc {
                fun outer() {
                    fun inner() {
                        transaction { 1 }
                    }
                    inner()
                }
            }
            """.trimIndent()
        assertEquals(listOf(0), BackendArchitectureOwners.transactionsInFunction(parse(nestedHelper), "outer"))
        assertEquals(listOf(1), BackendArchitectureOwners.transactionsInFunction(parse(nestedHelper), "inner"))
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.transactionsInFunction(parse("package f\nfun a() = 1\n"), "missing"),
        )
    }

    @Test
    fun `fixture - store transactions with writes fail while read wrappers pass`() {
        val readWrapper =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            import com.companyb.companyapp.commerce.WidgetTable
            object WidgetRepository {
                fun find(): Int = transaction { 1 }
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.storeTransactionWriteMixing(parse(readWrapper), setOf("WidgetTable")),
        )
        val writeMixing =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            import com.companyb.companyapp.commerce.WidgetTable
            object WidgetRepository {
                fun evil() {
                    transaction { WidgetTable.insert { } }
                }
            }
            """.trimIndent()
        assertEquals(
            listOf("evil: WidgetTable.insert"),
            BackendArchitectureOwners.storeTransactionWriteMixing(parse(writeMixing), setOf("WidgetTable")),
        )
        val nestedWriteStaysWithNested =
            """
            package f
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            import com.companyb.companyapp.commerce.WidgetTable
            object WidgetRepository {
                fun outer() {
                    transaction { 1 }
                    fun inner() {
                        WidgetTable.insert { }
                    }
                    inner()
                }
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.storeTransactionWriteMixing(
                parse(nestedWriteStaysWithNested),
                setOf("WidgetTable"),
            ),
        )
    }
}
