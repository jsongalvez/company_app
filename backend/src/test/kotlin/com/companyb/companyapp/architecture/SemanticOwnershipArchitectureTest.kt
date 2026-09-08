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
 * store check are subsumed here; transaction-count inventories
 * (Crud/Expense/Remittance/Attendance read-wrapper budgets, hand-parsed
 * command bodies, declaration-presence pins) retired #609 in favor of the
 * required-command and read-only-transaction invariants below.
 *
 * #608: persistence covers Tables and read-only mapped Views. Foreign mapping
 * reads need a recorded file-level projection grant even inside `internal`
 * types; FK declarations and `tableName` metadata need none; view writes fail
 * even for the owner; stale grants fail the tree.
 *
 * #609: comments and new read wrappers never alter a budget (PSI, per-function
 * counts). Removing a required mutation transaction fails the required-command
 * pin; detaching audit/recalculation fails the behavior atomicity suites
 * (Expense/Remittance/Attendance/Commission/Crud); nested store transactions
 * stay rejected.
 *
 * Pure PSI fixture proofs live in `SemanticOwnershipShapeFixtureTest` and
 * `SemanticOwnershipClassificationFixtureTest` (plus `ProjectionArchitectureTest`
 * and `WorkforceReadsArchitectureTest`) so this class stays under its
 * `LargeClass` pin (#634).
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

    @Test
    fun `each required command owns exactly one transaction`() {
        val required = BackendArchitectureOwners.requiredCommandTransactions
        assertTrue(required.isNotEmpty(), "required-command metadata must not be empty")
        val offenders =
            required.mapNotNull { entry ->
                val file = files[entry.file] ?: return@mapNotNull "${entry.file}: missing from tree"
                val counts = BackendArchitectureOwners.transactionsInFunction(file, entry.function)
                if (counts.size != 1) {
                    "${entry.file}: ${entry.function} matches ${counts.size} functions, want 1"
                } else if (counts.single() != 1) {
                    "${entry.file}: ${entry.function} owns ${counts.single()} transactions, want 1"
                } else {
                    null
                }
            }
        assertTrue(
            offenders.isEmpty(),
            "required commands must own exactly one transaction:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `stores open transactions only for reads`() {
        val allowed = BackendArchitectureOwners.allowedStoreWriteTransactions
        for (entry in allowed) {
            val file = files[entry.file] ?: error("recorded store write missing from tree: ${entry.file}")
            val mixing = BackendArchitectureOwners.storeTransactionWriteMixing(file, treeTables)
            assertTrue(
                mixing.any { it.substringBefore(":") == entry.function },
                "recorded store write no longer writes in a transaction (remove it): ${entry.file}: ${entry.function}",
            )
        }
        val offenders =
            files
                .filter { (path, _) -> BackendArchitectureOwners.roleOf(path) == BackendArchitectureOwners.STORE }
                .flatMap { (path, file) ->
                    BackendArchitectureOwners
                        .storeTransactionWriteMixing(file, treeTables)
                        .map { "$path: $it" }
                        .filter { line ->
                            val function = line.substringAfter(": ").substringBefore(":")
                            BackendArchitectureOwners.StoreWriteTransaction(
                                path,
                                function,
                            ) !in allowed
                        }
                }
        assertTrue(
            offenders.isEmpty(),
            "store-owned transactions must carry no writes:\n${offenders.joinToString("\n")}",
        )
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
