package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Semantic-owner architecture checks (#534, repaired #572, legacy scaffolding
 * retired #607, map #533).
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
 * the surviving count pins: every production whole-tree scan below covers the
 * completed feature layout, and resurrected legacy/shared paths fail the
 * classification closed (#607). The retired
 * BackendFeatureBoundaryArchitectureTest folder scopes and the BranchDay-only
 * store check are subsumed here; per-file transaction-count pins stay where
 * they guard exact wrapper budgets.
 *
 * #608: persistence covers Tables and read-only mapped Views. Foreign mapping
 * reads need a recorded file-level projection grant even inside `internal`
 * types; FK declarations and `tableName` metadata need none; view writes fail
 * even for the owner; stale grants fail the tree.
 */
class SemanticOwnershipArchitectureTest {
    private val sources: Map<String, String> by lazy { BackendArchitectureOwners.discover() }

    private val files: Map<String, KtFile> by lazy {
        sources.mapValues { (path, source) -> BackendArchitectureOwners.parseKt(source, path) }
    }

    private val treeTables: Set<String> by lazy { BackendArchitectureOwners.treeTableNames(files) }

    private val treeStores: Map<String, Set<String>> by lazy { BackendArchitectureOwners.treeStoreOwners(files) }

    private fun owner(path: String): String? = BackendArchitectureOwners.ownerOf(path)

    private fun isServiceScope(path: String): Boolean = TARGET_FEATURE_DIRS.any { path.startsWith(it) }

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
    fun `every production file has an explicit owner`() {
        val unclassified = sources.keys.filter { owner(it) == null }
        assertTrue(unclassified.isEmpty(), "unclassified production packages:\n${unclassified.joinToString("\n")}")
    }

    @Test
    fun `owner mapping covers the completed feature layout and nothing else`() {
        assertEquals("branchday", owner("branchday/BranchDayService.kt"))
        assertEquals("branch", owner("branch/BranchService.kt"))
        assertEquals("identity", owner("identity/AuthService.kt"))
        assertEquals("http", owner("api/routes/BranchDayRoutes.kt"))
        assertEquals("commission", owner("commission/CommissionService.kt"))
        assertEquals("remittance", owner("remittance/RemittanceService.kt"))
        assertEquals("finance", owner("finance/ExpenseService.kt"))
        assertEquals("session", owner("session/SessionService.kt"))
        assertEquals("session", owner("session/dashboard/DashboardService.kt"))
        assertEquals("reporting", owner("reporting/DailySalesSummaryService.kt"))
        assertEquals("mechanism", owner("utils/CursorCodec.kt"))
        assertEquals("app", owner("Main.kt"))
    }

    @Test
    fun `resurrected legacy and shared paths stay unclassified`() {
        val resurrected =
            listOf(
                "service/SessionService.kt",
                "service/BranchService.kt",
                "service/branchday/BranchDayService.kt",
                "service/finance/commission/CommissionService.kt",
                "service/finance/remittance/RemittanceService.kt",
                "service/dashboard/DashboardService.kt",
                "service/export/ExportService.kt",
                "repository/WidgetRepository.kt",
                "repository/model/WidgetTable.kt",
                "repository/BranchRepository.kt",
                "auth/OldAuth.kt",
                "config/AppConfig.kt",
                "inventory/StockService.kt",
                "dashboard/DashboardService.kt",
                "export/ExportService.kt",
            )
        val classified = resurrected.filter { owner(it) != null }
        assertTrue(classified.isEmpty(), "resurrected paths must not classify:\n${classified.joinToString("\n")}")
    }

    @Test
    fun `role follows file shape not folder`() {
        assertEquals("http-adapter", BackendArchitectureOwners.roleOf("api/routes/SessionRoutes.kt"))
        assertEquals("http-adapter", BackendArchitectureOwners.roleOf("branch/BranchRoutes.kt"))
        assertEquals("http-adapter", BackendArchitectureOwners.roleOf("branchday/BranchDayRoutes.kt"))
        assertEquals("store", BackendArchitectureOwners.roleOf("client/ClientRepository.kt"))
        assertEquals("store", BackendArchitectureOwners.roleOf("client/ClientRepository.kt"))
        assertEquals("store", BackendArchitectureOwners.roleOf("branchday/BranchDayRepository.kt"))
        assertEquals("command", BackendArchitectureOwners.roleOf("client/ClientService.kt"))
        assertEquals("command", BackendArchitectureOwners.roleOf("branch/BranchService.kt"))
    }

    // ---- HTTP adapter (role-based: every *Routes.kt plus api/) ----

    @Test
    fun `http adapters stay persistence-free in every location`() {
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
        return tableOwner != importer
    }

    // ---- Cross-owner table/view projections (map #615 #608) ----

    @Test
    fun `cross-owner persistence reads require explicit projection grants`() {
        val tableOwners = treeTableOwners()
        val offenders =
            files.flatMap { (path, file) ->
                val importer = owner(path) ?: return@flatMap emptyList()
                BackendArchitectureOwners
                    .foreignTableReads(path, importer, file, tableOwners)
                    .map { "$path: $it" }
            }
        assertTrue(
            offenders.isEmpty(),
            "unrecorded foreign persistence reads:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `projection grants stay used with no stale entries`() {
        val stale = BackendArchitectureOwners.unusedProjectionGrants(files, treeTableOwners())
        assertTrue(
            stale.isEmpty(),
            "stale projection grants (remove with the projection):\n${stale.joinToString("\n")}",
        )
    }

    @Test
    fun `mapped views stay read-only even for the owning feature`() {
        val offenders =
            files.flatMap { (path, file) ->
                BackendArchitectureOwners.viewWriteOps(file, treeTables).map { "$path: $it" }
            }
        assertTrue(offenders.isEmpty(), "view writes are never legal:\n${offenders.joinToString("\n")}")
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
        val scheduler = sources["notification/NextAppointmentScheduler.kt"] ?: error("scheduler missing from tree")
        assertTrue(scheduler.contains("ZonedDateTime.now(clock)"), "scheduler reads only its injected clock")
        assertTrue(
            scheduler.contains("BranchDayService.currentOperationalDate("),
            "run date comes from the operational-day authority",
        )
        assertTrue(!scheduler.contains(".toLocalDate()"), "scheduler must not derive a calendar date itself")
    }

    // ---- Fixtures (#572 acceptance: relocated files fail alike; #607: resurrections stay unclassified) ----

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
    fun `tables are declared only inside feature owners`() {
        val offenders =
            files.flatMap { (path, file) ->
                val tableOwner = owner(path)
                if (tableOwner == null || tableOwner in FEATURE_TABLE_OWNERS) {
                    emptyList()
                } else {
                    BackendArchitectureOwners.declaredTableNames(file).map { "$it in $path (owner $tableOwner)" }
                }
            }
        assertTrue(offenders.isEmpty(), "tables must live with their feature owner:\n${offenders.joinToString("\n")}")
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

    // ---- Authorization direction (#605) ----

    @Test
    fun `authorization never resolves feature resources or status policy`() {
        val offenders =
            files
                .filter { (path, _) -> owner(path) == "authorization" }
                .flatMap { (path, file) ->
                    BackendArchitectureOwners.authorizationFeatureLeaks(file).map { "$path: $it" }
                }
        assertTrue(
            offenders.isEmpty(),
            "authorization must not depend on feature resource/status-policy symbols:\n" +
                offenders.joinToString("\n"),
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

    private companion object {
        /**
         * Owners that may declare persistence tables (#607: no shared/mechanism tables;
         * #608: observability owns its `pg_stat_statements` view mapping for slow-query reads).
         */
        val FEATURE_TABLE_OWNERS =
            setOf(
                "identity",
                "authorization",
                "branch",
                "branchday",
                "workforce",
                "client",
                "session",
                "commerce",
                "finance",
                "commission",
                "remittance",
                "reporting",
                "audit",
                "notification",
                "observability",
            )
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
                "audit/",
                "notification/",
                "http/",
            )
    }
}
