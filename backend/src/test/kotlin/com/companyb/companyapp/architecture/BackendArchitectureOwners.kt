package com.companyb.companyapp.architecture

import io.github.detekt.parser.KtCompiler
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Semantic owner/seam policy for map #533 (#534, repaired #572, legacy
 * scaffolding retired #607).
 *
 * Single architecture-test owner: whole-source discovery plus a small explicit
 * owner mapping over the completed feature packages. Production movement
 * belonged to the feature children (#536–#551); this file moves no production
 * sources.
 *
 * Owner vocabulary follows #533: identity, authorization, branch, branchday,
 * workforce, client, session, commerce, finance, commission, remittance,
 * reporting, audit, notification; mechanisms http, database, observability,
 * logging, mechanism; app composition.
 *
 * #607: the completed feature locations are authoritative. There are no legacy
 * aliases, no service-root inventory, and no shared persistence bucket — a path
 * outside the mapping is unclassified and fails the tree closed. The opaque
 * cursor codec lives at `utils/` under the mechanism owner like the other
 * stateless mechanism helper there.
 *
 * #608: persistence means Tables and read-only mapped Views alike. Cross-owner
 * persistence reads are allowed only in recorded projection files
 * ([allowedProjectionReads]); `internal` visibility alone grants nothing.
 * Schema edges (FK `references()` inside a `Table` mapping, `tableName`
 * metadata) stay legal without a grant. Writes to mapped Views fail even for
 * the owning feature.
 *
 * #572: semantic owner (which feature) is separate from architectural role
 * (what shape: HTTP adapter, command, store). Moving a route beside its
 * feature changes owner, not role — role derives from file shape, so checks
 * follow colocated files instead of going vacuously green. All
 * declaration/import analysis uses Kotlin PSI via Detekt's parser (#572);
 * the previous hand-written comment/string lexer, brace matcher and
 * fun-declaration regexes are gone.
 */
object BackendArchitectureOwners {
    const val BASE_PACKAGE = "com.companyb.companyapp"
    const val HTTP_ADAPTER = "http-adapter"
    const val STORE = "store"
    const val COMMAND = "command"
    const val OTHER = "other"

    val mainRoot: File = File("backend/src/main/kotlin/com/companyb/companyapp")

    /** Whole-source discovery; fail-closed on empty or missing roots. */
    fun discover(root: File = mainRoot): Map<String, String> {
        val sources =
            if (!root.exists()) {
                emptyMap()
            } else {
                root
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .associate { it.relativeTo(root).path.replace(File.separatorChar, '/') to it.readText() }
            }
        require(sources.isNotEmpty()) { "source discovery found no Kotlin files under $root" }
        return sources
    }

    /** Explicit owner for a path relative to [mainRoot]; null means unclassified (#607 fail-closed). */
    fun ownerOf(relativePath: String): String? {
        val path = relativePath.trimStart('/')
        if (path == "Main.kt") return "app"
        return OWNER_PREFIXES.firstOrNull { (prefix, _) -> path.startsWith(prefix) }?.second
    }

    /**
     * Architectural role for a path relative to [mainRoot]. Unlike [ownerOf],
     * the role follows file shape, so a colocated `branch/BranchRoutes.kt`
     * stays an HTTP adapter and a relocated `client/ClientRepository.kt`
     * stays a store (#572).
     */
    fun roleOf(relativePath: String): String {
        val path = relativePath.trimStart('/')
        val name = path.substringAfterLast('/')
        return when {
            name.endsWith("Routes.kt") || path.startsWith("api/") -> HTTP_ADAPTER
            name.endsWith("Repository.kt") || name.endsWith("Store.kt") -> STORE
            name.endsWith("Service.kt") -> COMMAND
            else -> OTHER
        }
    }

    private val OWNER_PREFIXES: List<Pair<String, String>> =
        listOf(
            "branchday/" to "branchday",
            "workforce/" to "workforce",
            "session/" to "session",
            "commerce/" to "commerce",
            "commission/" to "commission",
            "remittance/" to "remittance",
            "finance/" to "finance",
            "reporting/" to "reporting",
            "audit/" to "audit",
            "notification/" to "notification",
            "identity/" to "identity",
            "authorization/" to "authorization",
            "branch/" to "branch",
            "client/" to "client",
            "api/" to "http",
            "http/" to "http",
            "app/" to "app",
            "database/" to "database",
            "exception/" to "mechanism",
            "utils/" to "mechanism",
            "logging/" to "logging",
            "observability/" to "observability",
        )

    /** Recorded Instant.now owners (#322, retained): auth lifecycle, branch-day clock, incident filing. */
    val instantOwners: Set<String> =
        setOf(
            "identity/JwtService.kt",
            "observability/IncidentService.kt",
            "identity/AuthService.kt",
            "identity/UserService.kt",
            "branchday/BranchDayService.kt",
        )

    /**
     * Deliberate cross-owner store reads (read projections with an explicit
     * grant). Record projection grants here — distinctly from command
     * coordination, which flows service-to-service and needs no entry —
     * never as an all-to-all graph. An empty set stays legal and requires no
     * dummy entry (#572). Map #615 #604 retired the last three workforce grants:
     * session/commission now read membership/slot/attendance facts through the
     * workforce seam instead of importing its stores.
     */
    val allowedStoreReads: Set<StoreSeam> = emptySet()

    /** One granted cross-owner store read: [importerOwner] may read [store] (fully qualified). */
    data class StoreSeam(
        val importerOwner: String,
        val store: String,
    )

    // ---- PSI parsing (Detekt's public parser; replaces the hand-written lexer) ----

    private val ktCompiler: KtCompiler by lazy { KtCompiler() }

    private val snippetDir: Path by lazy { Files.createTempDirectory("arch-snippet") }

    /**
     * Parses Kotlin source to a [KtFile]; comments/strings stay inert PSI,
     * never code shapes. Whole-tree callers pass the repo-relative path so
     * the real file backs the parse; snippets fall back to a shared scratch
     * file — content always comes from [source], the path is only an
     * existence anchor for the parser.
     */
    fun parseKt(
        source: String,
        relativePath: String = "Snippet.kt",
    ): KtFile {
        val disk = mainRoot.resolve(relativePath.trimStart('/'))
        val path = if (disk.isFile) disk.toPath() else writeSnippet(relativePath, source)
        return ktCompiler.createKtFile(source, snippetDir, path)
    }

    private fun writeSnippet(
        relativePath: String,
        source: String,
    ): Path {
        val name = relativePath.substringAfterLast('/').takeIf { it.endsWith(".kt") } ?: "Snippet.kt"
        val file = snippetDir.resolve(name)
        Files.writeString(file, source)
        return file
    }

    /** Local name to fully qualified path for every import directive (aliases resolved). */
    fun importMap(file: KtFile): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (directive in file.importDirectives) {
            val path = directive.importPath?.pathStr ?: continue
            val alias = directive.alias?.name
            result[alias ?: path.substringAfterLast('.')] = path
        }
        return result
    }

    private fun <T : PsiElement> descendants(
        root: PsiElement,
        type: Class<T>,
    ): List<T> {
        val out = mutableListOf<T>()

        fun walk(element: PsiElement) {
            if (type.isInstance(element)) out.add(type.cast(element))
            element.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    private inline fun <reified T : PsiElement> KtFile.collect(): List<T> = descendants(this, T::class.java)

    private fun isInsideImport(element: PsiElement): Boolean =
        generateSequence(element.parent) { it.parent }.any { it is KtImportDirective }

    private fun nearestInternalType(element: PsiElement): KtClassOrObject? =
        generateSequence(element.parent) { it.parent }
            .filterIsInstance<KtClassOrObject>()
            .firstOrNull { it.hasModifier(KtTokens.INTERNAL_KEYWORD) }

    private fun calleeName(call: KtCallExpression): String? =
        (call.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()

    private fun isCallTo(
        call: KtCallExpression,
        method: String,
    ): Boolean = calleeName(call) == method

    private fun receiverText(call: KtCallExpression): String? =
        (call.parent as? KtDotQualifiedExpression)?.receiverExpression?.text

    /** Call sites of `Receiver.method(...)`; string/comment lookalikes never match (#572). */
    fun callSites(
        file: KtFile,
        receiver: String,
        method: String,
    ): Int = file.collect<KtCallExpression>().count { isCallTo(it, method) && receiverText(it) == receiver }

    // ---- Table and mapped-view knowledge (generic: any feature package, aliases, qualified) ----

    private fun isPersistenceName(name: String): Boolean =
        (name.endsWith("Table") || name.endsWith("View")) && name != "Table" && name != "View"

    private fun isTableSubtype(declaration: KtClassOrObject): Boolean =
        declaration.superTypeListEntries.any { it.text.trimStart().startsWith("Table(") }

    private fun isOurTablePath(path: String): Boolean {
        if (!path.startsWith("$BASE_PACKAGE.")) return false
        return isPersistenceName(path.substringAfterLast('.'))
    }

    /** Local name to persistence simple name for our table/view imports, including `as` aliases. */
    fun ourTableImports(file: KtFile): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((local, path) in importMap(file)) {
            if (path.endsWith(".*") || !isOurTablePath(path)) continue
            result[local] = path.substringAfterLast('.')
        }
        return result
    }

    /** Persistence-mapping simple names declared in this file (#608: `Table` subtypes and Table/View names). */
    fun declaredTableNames(file: KtFile): Set<String> =
        file.collect<KtClassOrObject>().mapNotNullTo(mutableSetOf()) {
            it.name?.takeIf { name -> it is KtObjectDeclaration && (isPersistenceName(name) || isTableSubtype(it)) }
        }

    private fun resolveTable(
        name: String,
        imports: Map<String, String>,
        visibleTables: Set<String>,
        qualifiedRoot: String?,
    ): String? =
        imports[name] ?: visibleTables.takeIf { name in it }?.let { name }
            ?: name.takeIf { isPersistenceName(it) && qualifiedRoot?.startsWith("$BASE_PACKAGE.") == true }

    private fun outermostQualifiedText(element: PsiElement): String? {
        var current: PsiElement = element
        while (current.parent is KtDotQualifiedExpression) current = current.parent
        return (current as? KtDotQualifiedExpression)?.text
    }

    /**
     * Our-table references used outside an `internal` type body.
     * [visibleTables] is the tables the file may name without an import:
     * whole-tree callers pass the tree-wide table names, fixtures pass the
     * snippet's own declarations. Order-independent: a seam may precede or
     * follow the use (#572 — no declaration-order rule).
     */
    fun tableLeaks(
        file: KtFile,
        visibleTables: Set<String>,
    ): List<String> {
        val imports = ourTableImports(file)
        return file
            .collect<KtSimpleNameExpression>()
            .filter { !isInsideImport(it) && nearestInternalType(it) == null }
            .mapNotNull { resolveTable(it.getReferencedName(), imports, visibleTables, outermostQualifiedText(it)) }
            .distinct()
    }

    /**
     * Table-receiver DML entry points, closed against the Exposed 1.3.1
     * `QueriesKt` surface (verified with `javap`; re-derive on version bumps):
     * every function there taking a `Table` (or `Join`) receiver is listed.
     * Join-receiver overloads (`update(Join, …)`, `delete(Join, …)`) resolve
     * through the receiver-subtree fallback in [tableWriteOps].
     */
    private val TABLE_WRITE_OPS =
        setOf(
            "insert",
            "insertIgnore",
            "insertAndGetId",
            "insertIgnoreAndGetId",
            "insertReturning",
            "batchInsert",
            "replace",
            "batchReplace",
            "update",
            "updateReturning",
            "delete",
            "deleteWhere",
            "deleteIgnoreWhere",
            "deleteAll",
            "deleteReturning",
            "upsert",
            "upsertReturning",
            "batchUpsert",
            "mergeFrom",
        )

    /**
     * `Table.writeOp(...)` sites as `Table.op`. Receiver resolves through
     * aliases, same-package declarations and qualified paths, like [tableLeaks].
     * When the receiver is not a plain table reference (parenthesized tables,
     * join expressions for the `update(Join, …)` / `delete(Join, …)` overloads),
     * every table named inside the receiver subtree reports the op, so a join
     * delete cannot hide its targets.
     */
    fun tableWriteOps(
        file: KtFile,
        visibleTables: Set<String>,
    ): List<String> {
        val imports = ourTableImports(file)
        return file
            .collect<KtCallExpression>()
            .filter { isCallTo(it, TABLE_WRITE_OPS) }
            .flatMap { call ->
                val receiver =
                    (call.parent as? KtDotQualifiedExpression)?.receiverExpression ?: return@flatMap emptyList()
                val direct =
                    when (receiver) {
                        is KtSimpleNameExpression -> receiver.getReferencedName()
                        is KtDotQualifiedExpression -> receiver.text.substringAfterLast('.').substringBefore('<')
                        else -> null
                    }?.let { resolveTable(it, imports, visibleTables, outermostQualifiedText(receiver)) }
                if (direct != null) {
                    listOf("$direct.${calleeName(call)}")
                } else {
                    receiverTables(receiver, imports, visibleTables).map { "$it.${calleeName(call)}" }
                }
            }.distinct()
    }

    private fun receiverTables(
        receiver: PsiElement,
        imports: Map<String, String>,
        visibleTables: Set<String>,
    ): List<String> =
        descendants(receiver, KtSimpleNameExpression::class.java)
            .filter { !isInsideImport(it) }
            .mapNotNull { resolveTable(it.getReferencedName(), imports, visibleTables, outermostQualifiedText(it)) }
            .distinct()

    private fun isCallTo(
        call: KtCallExpression,
        methods: Set<String>,
    ): Boolean = calleeName(call) in methods

    // ---- HTTP adapter (role-based: every *Routes.kt plus api/) ----

    /** HTTP-adapter rule: no Exposed, transaction, our-table, or raw-exec shapes. */
    fun apiLayerViolations(file: KtFile): List<String> =
        buildList {
            if (file.importDirectives.any { it.importPath?.pathStr?.startsWith("org.jetbrains.exposed") == true }) {
                add("exposed-import")
            }
            val calls = file.collect<KtCallExpression>()
            if (calls.any { isCallTo(it, "transaction") }) add("transaction-block")
            if (ourTableImports(file).isNotEmpty()) add("persistence-table-import")
            if (calls.any { isCallTo(it, "exec") }) add("raw-sql-exec")
        }

    // ---- Store visibility ----

    /** Top-level store declarations missing `internal`/`private` (service scope). */
    fun nonInternalStoreDeclarations(file: KtFile): List<String> =
        file.declarations
            .filterIsInstance<KtClassOrObject>()
            .filter { (it.name?.endsWith("Repository") == true || it.name?.endsWith("Store") == true) }
            .filter { !it.hasModifier(KtTokens.INTERNAL_KEYWORD) && !it.hasModifier(KtTokens.PRIVATE_KEYWORD) }
            .map { "${if (it is KtObjectDeclaration) "object" else "class"} ${it.name}" }

    // ---- Audit ownership (role-based: every store, wherever it lives) ----

    /**
     * Audit append-seam receivers (#549): the current [AuditLog] seam plus the
     * retired `AuditLogRepository` name, so a relocated store writing an audit
     * row fails under either spelling.
     */
    private val AUDIT_SEAM_RECEIVERS = setOf("AuditLog", "AuditLogRepository")

    fun containsAuditRecordCall(file: KtFile): Boolean =
        file.collect<KtCallExpression>().any {
            calleeName(it)?.startsWith("record") == true && receiverText(it) in AUDIT_SEAM_RECEIVERS
        }

    fun containsAuditFn(file: KtFile): Boolean =
        file.collect<KtSimpleNameExpression>().any { !isInsideImport(it) && it.getReferencedName() == "auditFn" } ||
            file.collect<KtNamedDeclaration>().any { it.name == "auditFn" }

    // ---- Command transactions (PSI: generics, extensions, nested, expression bodies) ----

    private fun ownTransactionCalls(function: KtNamedFunction): Int {
        var count = 0

        fun walk(element: PsiElement) {
            if (element !== function && element is KtNamedFunction) return
            if (element is KtCallExpression && isCallTo(element, "transaction")) count++
            element.children.forEach(::walk)
        }
        function.children.forEach(::walk)
        return count
    }

    /**
     * Highest transaction-block count inside any single function body; commands
     * own at most one. Each function — generic, extension, nested, annotated,
     * expression-bodied — is judged on its own body; nested functions never
     * leak their blocks into the outer count nor escape analysis (#572).
     */
    fun maxTransactionsPerFunction(file: KtFile): Int =
        file.collect<KtNamedFunction>().maxOfOrNull(::ownTransactionCalls) ?: 0

    /** In-transaction stores must open no transaction of their own (ADR-0024). */
    fun inTransactionFunctionsWithNestedTransaction(file: KtFile): List<String> =
        file
            .collect<KtNamedFunction>()
            .filter { "InTransaction" in (it.name ?: "") && ownTransactionCalls(it) > 0 }
            .mapNotNull { it.name }

    // ---- Cross-feature stores (generic; replaces the BranchDay-only check) ----

    /** Top-level `*Repository`/`*Store` declarations in this file. */
    fun declaredStoreNames(file: KtFile): Set<String> =
        file.collect<KtClassOrObject>().mapNotNullTo(mutableSetOf()) {
            it.name?.takeIf { name -> name.endsWith("Repository") || name.endsWith("Store") }
        }

    private fun isForeignStore(
        store: String,
        importerOwner: String,
        storeOwners: Map<String, Set<String>>,
    ): Boolean = storeOwners[store]?.any { it != importerOwner } == true

    private fun importedStoreOffenders(
        importerOwner: String,
        file: KtFile,
        storeOwners: Map<String, Set<String>>,
        allowed: Set<StoreSeam>,
    ): List<String> =
        importMap(file)
            .values
            .filter { it.startsWith("$BASE_PACKAGE.") && !it.endsWith(".*") }
            .map { it.substringAfterLast('.') to it }
            .filter { (simple, _) -> simple.endsWith("Repository") || simple.endsWith("Store") }
            .filter { (simple, path) ->
                isForeignStore(simple, importerOwner, storeOwners) && StoreSeam(importerOwner, path) !in allowed
            }.map { (_, path) -> "import $path" }

    private fun usedStoreOffenders(
        importerOwner: String,
        file: KtFile,
        storeOwners: Map<String, Set<String>>,
        allowed: Set<StoreSeam>,
    ): List<String> {
        val imports = importMap(file)
        val declared = declaredStoreNames(file) + file.collect<KtNamedFunction>().mapNotNull { it.name }
        return file
            .collect<KtSimpleNameExpression>()
            .filter { !isInsideImport(it) }
            .map { it.getReferencedName() to outermostQualifiedText(it) }
            .filter { (name, _) -> name !in imports && name !in declared }
            .filter { (name, _) ->
                (name.endsWith("Repository") || name.endsWith("Store")) &&
                    isForeignStore(name, importerOwner, storeOwners)
            }.mapNotNull { (name, qualified) ->
                val fq = qualified?.substringBefore('(')?.substringBefore('<') ?: name
                fq
                    .takeIf { it.startsWith("$BASE_PACKAGE.") || it == name }
                    ?.takeIf {
                        StoreSeam(importerOwner, it) !in allowed &&
                            imports.values.none { path -> path.endsWith(".$name") }
                    }?.let { "use $it" }
            }.distinct()
    }

    /**
     * Foreign-feature store uses: imports, same-package uses and qualified
     * references resolving to a store owned by another feature. Both sides
     * being `internal` grants nothing — Kotlin `internal` is module-wide
     * (#572). [storeOwners] maps store name to owning owners (tree-wide
     * derivation over classified files only — an unclassified path contributes
     * no ownership, so a resurrected shared location legalizes nothing, #607).
     * [allowed] carries explicit read-projection grants.
     */
    fun foreignStoreRefs(
        importerOwner: String,
        file: KtFile,
        storeOwners: Map<String, Set<String>>,
        allowed: Set<StoreSeam> = allowedStoreReads,
    ): List<String> {
        if (storeOwners.isEmpty()) return emptyList()
        return importedStoreOffenders(importerOwner, file, storeOwners, allowed) +
            usedStoreOffenders(importerOwner, file, storeOwners, allowed)
    }

    // ---- Tree-wide declaration indexes (symbols from declarations, #572) ----

    /** Store name to owning owners, derived from top-level declarations tree-wide. */
    fun treeStoreOwners(files: Map<String, KtFile>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        for ((path, file) in files) {
            val owner = ownerOf(path) ?: continue
            for (store in declaredStoreNames(file)) result.getOrPut(store) { mutableSetOf() }.add(owner)
        }
        return result
    }

    /** Every table name declared tree-wide (import aliases resolve against these). */
    fun treeTableNames(files: Map<String, KtFile>): Set<String> =
        files.values.flatMapTo(mutableSetOf()) { declaredTableNames(it) }

    // ---- Cross-owner table/view projections (map #615 #608) ----

    /**
     * One granted cross-owner persistence read: [importerFile] (relative to
     * [mainRoot], e.g. `identity/MeRepository.kt`) may read [table] (simple
     * persistence-mapping name, e.g. `BranchTable`). File-level granularity:
     * the smallest unit that keeps one batched join attributable without
     * splitting co-located store helpers.
     */
    data class ProjectionGrant(
        val importerFile: String,
        val table: String,
    )

    /**
     * Recorded intentional projection reads (#608). Batched joins stay joins —
     * no N+1 service-call rewrite — but each foreign mapping read lives in
     * exactly one recorded file. Pure FK `references()` edges inside `Table`
     * mappings and `tableName` metadata need no entry; command coordination
     * crosses semantic seams (`SessionReads`, `WorkforceReads`, …) and needs
     * no entry either. An unused entry fails the tree stale — remove the grant
     * with the projection.
     */
    val allowedProjectionReads: Set<ProjectionGrant> =
        setOf(
            ProjectionGrant("audit/AuditLogStore.kt", "AppUserTable"),
            ProjectionGrant("audit/AuditLogStore.kt", "BranchTable"),
            ProjectionGrant("client/ClientRepository.kt", "SessionTable"),
            ProjectionGrant("client/ClientRepository.kt", "ActiveSessionVoidsView"),
            ProjectionGrant("commerce/BranchInventoryRepository.kt", "BranchDayTable"),
            ProjectionGrant("commerce/ProductSaleRepository.kt", "SessionTable"),
            ProjectionGrant("commerce/ProductSaleRepository.kt", "ActiveSessionVoidsView"),
            ProjectionGrant("finance/CompensationRepository.kt", "AppUserTable"),
            ProjectionGrant("identity/MeRepository.kt", "BranchTable"),
            ProjectionGrant("identity/MeRepository.kt", "BranchDayTable"),
            ProjectionGrant("identity/MeRepository.kt", "AttendanceTable"),
            ProjectionGrant("identity/MeRepository.kt", "UserBranchAssignmentTable"),
            ProjectionGrant("identity/RoleRepository.kt", "CapabilityTable"),
            ProjectionGrant("identity/UserRepository.kt", "BranchTable"),
            ProjectionGrant("identity/UserRepository.kt", "UserBranchAssignmentTable"),
            ProjectionGrant("notification/NextAppointmentRepository.kt", "SessionTable"),
            ProjectionGrant("notification/NextAppointmentRepository.kt", "ActiveSessionVoidsView"),
            ProjectionGrant("notification/NextAppointmentRepository.kt", "BranchDayTable"),
            ProjectionGrant("notification/NextAppointmentRepository.kt", "UserBranchAssignmentTable"),
            ProjectionGrant("notification/NextAppointmentRepository.kt", "ActiveUserCapabilitiesView"),
            ProjectionGrant("notification/NextAppointmentRepository.kt", "CapabilityTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "BranchDayTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "ClientTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "ProductSaleTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "CompensationTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "ExpenseTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "SessionTable"),
            ProjectionGrant("remittance/RemittanceRepository.kt", "ActiveSessionVoidsView"),
            ProjectionGrant("reporting/ExportRepository.kt", "BranchTable"),
            ProjectionGrant("session/SessionBaseRateRepository.kt", "BranchTable"),
            ProjectionGrant("session/SessionRepository.kt", "BranchTable"),
            ProjectionGrant("session/SessionRepository.kt", "BranchDayTable"),
            ProjectionGrant("session/dashboard/DashboardRepository.kt", "AppUserTable"),
            ProjectionGrant("session/dashboard/DashboardRepository.kt", "ClientTable"),
            ProjectionGrant("workforce/AttendanceRepository.kt", "AppUserTable"),
            ProjectionGrant("workforce/BranchMemberRepository.kt", "AppUserTable"),
            ProjectionGrant("workforce/relief/ReliefAccessRepository.kt", "BranchTable"),
            ProjectionGrant("workforce/relief/ReliefAccessRepository.kt", "BranchDayTable"),
            ProjectionGrant("workforce/relief/ReliefAccessRepository.kt", "AppUserTable"),
            ProjectionGrant("workforce/relief/ReliefInviteRepository.kt", "BranchTable"),
            ProjectionGrant("workforce/relief/ReliefInviteRepository.kt", "BranchDayTable"),
            ProjectionGrant("workforce/relief/ReliefInviteRepository.kt", "AppUserTable"),
            ProjectionGrant("workforce/relief/ReliefInviteRepository.kt", "ActiveUserCapabilitiesView"),
            ProjectionGrant("workforce/relief/ReliefInviteRepository.kt", "CapabilityTable"),
        )

    /**
     * True when [element] sits inside a `references(...)` call argument — the
     * FK schema edge (`javaUUID("x").references(ForeignTable.id)`). Only such
     * edges are exempt inside mapping bodies: a runtime query placed inside a
     * `Table` object (or a fake mapping-named helper) stays a projection read
     * and still needs a grant. The nearest enclosing call decides, so a
     * `selectAll()` or any other wrapper around the reference is not an edge.
     */
    private fun isFkReference(element: PsiElement): Boolean =
        generateSequence(element.parent) { it.parent }
            .filterIsInstance<KtCallExpression>()
            .firstOrNull()
            ?.let { calleeName(it) == "references" } == true

    /**
     * True for `Table.tableName` metadata in any spelling (`T.tableName`,
     * `com.pkg.T.tableName`, `T.tableName.length`): the mapping's name string,
     * never a data read. Column reads (`T.col`, `row[T.col]`) keep their
     * normal resolution — only a `.tableName` link in the chain exempts.
     */
    private fun isTableNameAccess(element: KtSimpleNameExpression): Boolean {
        val direct = element.parent as? KtDotQualifiedExpression
        if (direct?.selectorExpression?.text == "tableName") return true
        val qualified = outermostQualifiedText(element) ?: return false
        return qualified.substringBefore('(').substringBefore('<').endsWith(".tableName")
    }

    /**
     * Foreign-owner persistence reads used for data: imports, aliases,
     * same-package and qualified references resolving to a mapping owned by
     * another feature. `internal` visibility grants nothing. Schema edges stay
     * legal without a grant: FK `references()` arguments and `tableName`
     * metadata. Comments/strings stay inert PSI, never reads. [tableOwners]
     * maps mapping name to its owner and doubles as the visible set
     * (same-package resolution); an unclassified path contributes no ownership
     * (#607), so a resurrected shared location legalizes nothing. [allowed]
     * carries the recorded file-level projection grants.
     */
    fun foreignTableReads(
        importerFile: String,
        importerOwner: String,
        file: KtFile,
        tableOwners: Map<String, String>,
        allowed: Set<ProjectionGrant> = allowedProjectionReads,
    ): List<String> {
        if (tableOwners.isEmpty()) return emptyList()
        val imports = ourTableImports(file)
        val visibleTables = tableOwners.keys
        return file
            .collect<KtSimpleNameExpression>()
            .filter { !isInsideImport(it) && !isFkReference(it) && !isTableNameAccess(it) }
            .mapNotNull { resolveTable(it.getReferencedName(), imports, visibleTables, outermostQualifiedText(it)) }
            .filter { table -> tableOwners[table]?.let { it != importerOwner } == true }
            .filter { table -> ProjectionGrant(importerFile, table) !in allowed }
            .distinct()
    }

    /**
     * Recorded grants no live file uses: renamed/removed projections must take
     * their grant with them. Reports `file: table` entries.
     */
    fun unusedProjectionGrants(
        files: Map<String, KtFile>,
        tableOwners: Map<String, String>,
        allowed: Set<ProjectionGrant> = allowedProjectionReads,
    ): List<String> {
        val used = mutableSetOf<ProjectionGrant>()
        for ((path, file) in files) {
            val owner = ownerOf(path) ?: continue
            for (table in foreignTableReads(path, owner, file, tableOwners, emptySet())) {
                used.add(ProjectionGrant(path, table))
            }
        }
        return allowed.filter { it !in used }.map { "${it.importerFile}: ${it.table}" }
    }

    /**
     * `View.writeOp(...)` sites as `View.op`. Mapped views are read-only —
     * even the owning feature never inserts/updates/deletes against them.
     */
    fun viewWriteOps(
        file: KtFile,
        visibleTables: Set<String>,
    ): List<String> = tableWriteOps(file, visibleTables).filter { it.substringBefore('.').endsWith("View") }

    // ---- Clock ownership (PSI call shapes; prose never matches) ----

    fun containsManilaZone(file: KtFile): Boolean = callSites(file, "ZoneId", "of") > 0

    fun containsIndependentToday(file: KtFile): Boolean =
        callSites(file, "LocalDate", "now") + callSites(file, "OffsetDateTime", "now") > 0

    fun containsInstantNow(file: KtFile): Boolean = callSites(file, "Instant", "now") > 0

    // ---- Authorization direction (#605) ----

    /**
     * Feature-specific symbols the authorization owner must never import (#605).
     * Authorization owns capability-context evaluation and small HTTP adaptations
     * for already-resolved scope; each feature's HTTP adapter owns how its
     * resource resolves that scope and any operation-specific rule.
     *
     * Exact symbols only — never a package-wide ban: low-level capability-view
     * inputs (e.g. `contracts.authorization.*`, the Branch Day operational-day
     * seam) stay legitimate.
     */
    val authorizationBannedImports: Set<String> =
        setOf(
            "com.companyb.companyapp.session.SessionReads",
            "com.companyb.companyapp.finance.FinanceReads",
            "com.companyb.companyapp.remittance.RemittanceService",
            "com.companyb.companyapp.contracts.session.SessionStatus",
            "com.companyb.companyapp.contracts.session.isStatusCorrection",
        )

    /** Banned feature-specific imports used by an authorization-owned file. */
    fun authorizationFeatureLeaks(file: KtFile): List<String> {
        val importLeaks =
            importMap(file)
                .values
                .filter { it in authorizationBannedImports }
                .map { "import $it" }
        // Symbol-exact usage check (no package-wide ban): every compilable evasion of
        // the import rule — wildcard imports, fully-qualified references, same-package
        // coincidences aside — still names one of these symbols outside an import
        // directive, and the authorization owner has zero legitimate use for any of
        // them. Comments/strings stay inert PSI, never leaks.
        val usageLeaks =
            file
                .collect<KtSimpleNameExpression>()
                .filter { !isInsideImport(it) }
                .map { it.getReferencedName() }
                .filter { name ->
                    authorizationBannedImports.any { it.substringAfterLast('.') == name }
                }.distinct()
                .map { "use $it" }
        return importLeaks + usageLeaks
    }
}
