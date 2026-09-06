package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Semantic-owner architecture checks (#534, repaired #572, map #533).
 *
 * Owner (which feature) is separate from role (what shape): HTTP-adapter
 * checks follow every `*Routes.kt` plus `api/` wherever they live, and
 * store checks follow every `*Repository.kt`/`*Store.kt` — a colocated
 * `branch/BranchRoutes.kt` or `client/ClientRepository.kt` can no longer
 * dodge its rule by moving (#572). Table/store symbols derive from
 * declarations and resolve through imports, aliases, same-package
 * declarations and qualified paths; transaction shapes parse as PSI, so
 * generics, extensions, nested and expression-bodied functions parse
 * uniformly. No rule depends on declaration order.
 *
 * Stronger replacements for the folder-pinned predecessors, kept alongside
 * the surviving count pins: every production whole-tree scan below covers
 * legacy and target layouts. The retired
 * BackendFeatureBoundaryArchitectureTest folder scopes and the BranchDay-only
 * store check are subsumed here; per-file transaction-count pins stay where
 * they guard exact wrapper budgets.
 */
class SemanticOwnershipArchitectureTest {
    private val sources: Map<String, String> by lazy { BackendArchitectureOwners.discover() }

    private val files: Map<String, KtFile> by lazy {
        sources.mapValues { (path, source) -> BackendArchitectureOwners.parseKt(source, path) }
    }

    private val treeTables: Set<String> by lazy { BackendArchitectureOwners.treeTableNames(files) }

    private val treeStores: Map<String, Set<String>> by lazy { BackendArchitectureOwners.treeStoreOwners(files) }

    private fun owner(path: String): String? = BackendArchitectureOwners.ownerOf(path)

    private fun isServiceScope(path: String): Boolean =
        path.startsWith("service/") ||
            TARGET_FEATURE_DIRS.any { path.startsWith(it) }

    // ---- Discovery (replaces api/service/repository-only scans) ----

    @Test
    fun `discovery covers the whole backend tree and fails closed`() {
        assertTrue(sources.isNotEmpty(), "whole-source discovery must find the backend tree")
        val empty =
            java.nio.file.Files
                .createTempDirectory("empty-arch-discovery")
                .toFile()
        try {
            assertFailsWith<IllegalArgumentException> { BackendArchitectureOwners.discover(empty) }
        } finally {
            empty.deleteRecursively()
        }
        assertFailsWith<IllegalArgumentException> {
            BackendArchitectureOwners.discover(java.io.File("backend/src/test/fixtures/does-not-exist-534"))
        }
    }

    @Test
    fun `every production file has an explicit owner in legacy or target packages`() {
        val unclassified = sources.keys.filter { owner(it) == null }
        assertTrue(unclassified.isEmpty(), "unclassified production packages:\n${unclassified.joinToString("\n")}")
    }

    @Test
    fun `owner mapping covers legacy and target packages so deletion cannot pass silently`() {
        assertEquals("branchday", owner("service/branchday/BranchDayService.kt"))
        assertEquals("branchday", owner("branchday/BranchDayService.kt"))
        assertEquals("branch", owner("service/BranchService.kt"))
        assertEquals("branch", owner("branch/BranchService.kt"))
        assertEquals("identity", owner("service/AuthService.kt"))
        assertEquals("identity", owner("identity/AuthService.kt"))
        assertEquals("http", owner("api/routes/BranchDayRoutes.kt"))
        assertEquals("persistence", owner("repository/BranchRepository.kt"))
        assertEquals("commission", owner("service/finance/commission/CommissionService.kt"))
        assertEquals("remittance", owner("service/finance/remittance/RemittanceService.kt"))
        assertEquals("finance", owner("service/finance/ExpenseService.kt"))
        assertEquals("finance", owner("finance/ExpenseService.kt"))
        assertEquals("session", owner("service/session/SessionService.kt"))
        assertEquals("session", owner("session/SessionService.kt"))
        assertEquals("reporting", owner("service/dashboard/DashboardService.kt"))
        assertEquals("reporting", owner("dashboard/DashboardService.kt"))
        assertEquals("reporting", owner("service/export/ExportService.kt"))
    }

    @Test
    fun `role follows file shape not folder`() {
        assertEquals("http-adapter", BackendArchitectureOwners.roleOf("api/routes/SessionRoutes.kt"))
        assertEquals("http-adapter", BackendArchitectureOwners.roleOf("branch/BranchRoutes.kt"))
        assertEquals("http-adapter", BackendArchitectureOwners.roleOf("branchday/BranchDayRoutes.kt"))
        assertEquals("store", BackendArchitectureOwners.roleOf("repository/ClientRepository.kt"))
        assertEquals("store", BackendArchitectureOwners.roleOf("client/ClientRepository.kt"))
        assertEquals("store", BackendArchitectureOwners.roleOf("branchday/BranchDayRepository.kt"))
        assertEquals("command", BackendArchitectureOwners.roleOf("service/ClientService.kt"))
        assertEquals("command", BackendArchitectureOwners.roleOf("branch/BranchService.kt"))
    }

    // ---- HTTP adapter (role-based: legacy routes and colocated feature routes) ----

    @Test
    fun `http adapters stay persistence-free in legacy and target locations`() {
        val offenders =
            files
                .filter { (path, _) -> isHttpAdapter(path) }
                .flatMap { (path, file) ->
                    BackendArchitectureOwners.apiLayerViolations(file).map { "$path: $it" }
                }
        assertTrue(offenders.isEmpty(), "http adapters must not touch persistence:\n${offenders.joinToString("\n")}")
    }

    private fun isHttpAdapter(path: String): Boolean =
        BackendArchitectureOwners.roleOf(path) == BackendArchitectureOwners.HTTP_ADAPTER

    // ---- Seam hiding (order-independent, alias/qualified-aware) ----

    @Test
    fun `feature surfaces hide persistence tables behind internal seams`() {
        val offenders =
            files
                .filter { (path, _) -> isServiceScope(path) }
                .flatMap { (path, file) ->
                    BackendArchitectureOwners.tableLeaks(file, treeTables).map { "$path: $it" }
                }
        assertTrue(offenders.isEmpty(), "public surfaces must not touch tables:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `feature-local stores stay internal`() {
        val offenders =
            files
                .filter { (path, _) -> isServiceScope(path) }
                .flatMap { (path, file) ->
                    BackendArchitectureOwners.nonInternalStoreDeclarations(file).map { "$path: $it" }
                }
        assertTrue(offenders.isEmpty(), "stores must be internal:\n${offenders.joinToString("\n")}")
    }

    // ---- Audit ownership (role-based: relocated stores included) ----

    @Test
    fun `stores never own audit writes wherever they live and auditFn stays retired everywhere`() {
        val recordCalls =
            files
                .filter { (path, _) -> BackendArchitectureOwners.roleOf(path) == BackendArchitectureOwners.STORE }
                .filter { (_, file) -> BackendArchitectureOwners.containsAuditRecordCall(file) }
                .keys
                .toList()
        assertTrue(
            recordCalls.isEmpty(),
            "audit writes belong to feature seams/commands:\n${recordCalls.joinToString("\n")}",
        )
        val auditFnFiles =
            files.filter { (_, file) -> BackendArchitectureOwners.containsAuditFn(file) }.keys.toList()
        assertTrue(auditFnFiles.isEmpty(), "auditFn callback must stay retired:\n${auditFnFiles.joinToString("\n")}")
    }

    // ---- Command transactions (PSI shapes, not file counts) ----

    @Test
    fun `each command owns at most one transaction without pinning file counts`() {
        val offenders =
            files
                .mapValues { (_, file) -> BackendArchitectureOwners.maxTransactionsPerFunction(file) }
                .filter { (_, max) -> max > 1 }
                .map { (path, max) -> "$path: $max transactions in one function" }
        assertTrue(offenders.isEmpty(), "commands must own at most one transaction:\n${offenders.joinToString("\n")}")
    }

    @Test
    fun `in-transaction stores open no transaction of their own`() {
        val offenders =
            files.flatMap { (path, file) ->
                BackendArchitectureOwners.inTransactionFunctionsWithNestedTransaction(file).map { "$path: $it" }
            }
        assertTrue(offenders.isEmpty(), "stores must not nest transactions:\n${offenders.joinToString("\n")}")
    }

    // ---- Feature table ownership (generic; replaces the BranchDay-only pin) ----

    @Test
    fun `feature-owned tables are written only by the owning feature`() {
        val tableOwners = treeTableOwners()
        val offenders =
            files.flatMap { (path, file) ->
                val importer = owner(path)
                BackendArchitectureOwners
                    .tableWriteOps(file, treeTables)
                    .filter { op -> isForeignTableWrite(op, importer, tableOwners) }
                    .map { "$path: $it" }
            }
        assertTrue(
            offenders.isEmpty(),
            "table writes belong to the owning feature:\n${offenders.joinToString("\n")}",
        )
    }

    private fun treeTableOwners(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((path, file) in files) {
            val tableOwner = owner(path) ?: continue
            for (table in BackendArchitectureOwners.declaredTableNames(file)) result.putIfAbsent(table, tableOwner)
        }
        return result
    }

    private fun isForeignTableWrite(
        op: String,
        importer: String?,
        tableOwners: Map<String, String>,
    ): Boolean {
        val table = op.substringBefore('.')
        val tableOwner = tableOwners[table] ?: return false
        if (tableOwner == BackendArchitectureOwners.LEGACY_SHARED) return false
        return tableOwner != importer
    }

    // ---- Cross-feature stores (generic; internal grants nothing) ----

    @Test
    fun `foreign feature stores are used only through recorded read seams`() {
        val offenders =
            files.flatMap { (path, file) ->
                val importer = owner(path) ?: return@flatMap emptyList()
                BackendArchitectureOwners.foreignStoreRefs(importer, file, treeStores).map { "$path: $it" }
            }
        assertTrue(offenders.isEmpty(), "unrecorded foreign store uses:\n${offenders.joinToString("\n")}")
    }

    // ---- Clock ownership (PSI call shapes; prose never matches) ----

    @Test
    fun `manila zone is constructed only inside the branchday owner`() {
        val sites = files.filter { (_, file) -> BackendArchitectureOwners.containsManilaZone(file) }.keys.toList()
        assertEquals(1, sites.size, "exactly one ZoneId site must exist: $sites")
        assertEquals("branchday", owner(sites.single()))
        assertTrue(
            "Asia/Manila" in sources.getValue(sites.single()),
            "the sole ZoneId site must construct the Manila zone",
        )
    }

    @Test
    fun `no consumer derives a calendar today independently`() {
        val offenders =
            files.filter { (_, file) -> BackendArchitectureOwners.containsIndependentToday(file) }.keys.toList()
        assertTrue(offenders.isEmpty(), "LocalDate.now/OffsetDateTime.now must not appear: $offenders")
    }

    @Test
    fun `instant reads stay limited to recorded owners and each record is consumed`() {
        val owners = BackendArchitectureOwners.instantOwners
        for (path in owners) {
            val file = files[path] ?: error("recorded clock owner missing from tree: $path")
            assertTrue(
                BackendArchitectureOwners.containsInstantNow(file),
                "recorded clock owner no longer reads Instant (remove it): $path",
            )
        }
        val offenders =
            files
                .filter { (path, _) -> path !in owners }
                .filter { (_, file) -> BackendArchitectureOwners.containsInstantNow(file) }
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

    // ---- Fixtures (#572 acceptance: legacy and target locations fail alike) ----

    private fun parse(source: String): KtFile = BackendArchitectureOwners.parseKt(source)

    @Test
    fun `fixture - http persistence access fails in legacy and colocated routes`() {
        val dirty =
            """
            package fixture
            import org.jetbrains.exposed.v1.jdbc.transactions.transaction
            import com.companyb.companyapp.repository.model.WidgetTable

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
            import com.companyb.companyapp.repository.model.WidgetTable

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
            import com.companyb.companyapp.repository.model.WidgetTable

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
                    import com.companyb.companyapp.repository.model.WidgetTable

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
                "finance/",
                "commission/",
                "remittance/",
                "reporting/",
                "dashboard/",
                "audit/",
                "notification/",
                "http/",
            )
    }
}
