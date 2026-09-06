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
 * Semantic owner/seam policy for map #533 (#534, repaired #572).
 *
 * Single architecture-test owner: whole-source discovery plus small explicit
 * owner mapping that supports legacy locations and #533 target packages side
 * by side. Production movement belongs to the feature children (#536, #537);
 * this file moves no production sources.
 *
 * Owner vocabulary follows #533: identity, authorization, branch, branchday,
 * workforce, client, session, commerce, finance, commission, remittance,
 * reporting, audit, notification; mechanisms http, database, observability,
 * logging; legacy buckets persistence (global repository layer) and
 * mechanism/app.
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

    /** Legacy shared bucket: stores/tables here stay importable until their feature move. */
    const val LEGACY_SHARED = "persistence"

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

    /** Explicit owner for a path relative to [mainRoot]; null means unclassified. */
    fun ownerOf(relativePath: String): String? {
        val path = relativePath.trimStart('/')
        val prefixed = OWNER_PREFIXES.firstOrNull { (prefix, _) -> path.startsWith(prefix) }?.second
        return prefixed ?: serviceRootFileOwner(path)
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

    private fun serviceRootFileOwner(path: String): String? =
        when {
            path == "Main.kt" -> "app"
            path.startsWith("service/") && '/' !in path.removePrefix("service/") -> serviceRootOwner(path)
            else -> null
        }

    /** Legacy service-root files predate feature directories; each maps explicitly. */
    fun serviceRootOwner(path: String): String? = SERVICE_ROOT_OWNERS[path.removePrefix("service/")]

    private val SERVICE_ROOT_OWNERS: Map<String, String> =
        mapOf(
            "AllowanceService.kt" to "commerce",
            "AuditLogReadScope.kt" to "audit",
            "AuditLogService.kt" to "audit",
            "AuthService.kt" to "identity",
            "BranchReadScope.kt" to "branch",
            "BranchService.kt" to "branch",
            "CapabilityService.kt" to "authorization",
            "ClientService.kt" to "client",
            "CompensationService.kt" to "commerce",
            "ConcernService.kt" to "session",
            "DailySalesSummaryService.kt" to "reporting",
            "ExpenseService.kt" to "commerce",
            "MeRepository.kt" to "identity",
            "MeService.kt" to "identity",
            "MedicalMissionDelegateService.kt" to "workforce",
            "MonthlyRemittanceSummaryService.kt" to "reporting",
            "NextAppointmentRepository.kt" to "notification",
            "NextAppointmentScheduler.kt" to "notification",
            "NotificationService.kt" to "notification",
            "ProductCategoryService.kt" to "commerce",
            "ProductSaleService.kt" to "commerce",
            "ProductService.kt" to "commerce",
            "ReliefAccessService.kt" to "workforce",
            "ReliefInviteReminderJob.kt" to "workforce",
            "ReliefInviteService.kt" to "workforce",
            "ReliefNotifications.kt" to "workforce",
            "ReliefRequestExpiryJob.kt" to "workforce",
            "SchedulerLifecycle.kt" to "app",
            "UserBranchAssignmentService.kt" to "workforce",
            "UserService.kt" to "identity",
        )

    private val OWNER_PREFIXES: List<Pair<String, String>> =
        listOf(
            "service/branchday/" to "branchday",
            "branchday/" to "branchday",
            "service/attendance/" to "workforce",
            "service/workforce/" to "workforce",
            "workforce/" to "workforce",
            "service/session/" to "session",
            "session/" to "session",
            "service/inventory/" to "commerce",
            "service/commerce/" to "commerce",
            "commerce/" to "commerce",
            "inventory/" to "commerce",
            "service/finance/commission/" to "commission",
            "service/commission/" to "commission",
            "commission/" to "commission",
            "service/finance/remittance/" to "remittance",
            "service/remittance/" to "remittance",
            "remittance/" to "remittance",
            "service/finance/" to "finance",
            "finance/" to "finance",
            "service/dashboard/" to "reporting",
            "dashboard/" to "reporting",
            "service/export/" to "reporting",
            "export/" to "reporting",
            "service/reporting/" to "reporting",
            "reporting/" to "reporting",
            "service/audit/" to "audit",
            "audit/" to "audit",
            "service/notification/" to "notification",
            "notification/" to "notification",
            "service/identity/" to "identity",
            "identity/" to "identity",
            "service/authorization/" to "authorization",
            "authorization/" to "authorization",
            "service/branch/" to "branch",
            "branch/" to "branch",
            "service/client/" to "client",
            "client/" to "client",
            "api/" to "http",
            "http/" to "http",
            "service/http/" to "http",
            "config/" to "http",
            "auth/" to "identity",
            "database/" to "database",
            "exception/" to "mechanism",
            "utils/" to "mechanism",
            "logging/" to "logging",
            "observability/" to "observability",
            "repository/" to "persistence",
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
     * dummy entry (#572).
     */
    val allowedStoreReads: Set<StoreSeam> =
        setOf(
            // Map #533: commission reads attendance facts without calling the attendance commands.
            StoreSeam("commission", "$BASE_PACKAGE.workforce.AttendanceRepository"),
            // #539: session reads workforce membership facts (member check, slot lookup) without
            // calling the workforce commands; command coordination stays service-to-service.
            StoreSeam("session", "$BASE_PACKAGE.workforce.UserBranchAssignmentRepository"),
            StoreSeam("session", "$BASE_PACKAGE.workforce.BranchMemberRepository"),
        )

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

    // ---- Table knowledge (generic: any feature package, aliases, qualified) ----

    private fun isOurTablePath(path: String): Boolean {
        if (!path.startsWith("$BASE_PACKAGE.")) return false
        val simple = path.substringAfterLast('.')
        return simple.endsWith("Table") && simple != "Table"
    }

    /** Local name to table simple name for our-table imports, including `as` aliases. */
    fun ourTableImports(file: KtFile): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((local, path) in importMap(file)) {
            if (path.endsWith(".*") || !isOurTablePath(path)) continue
            result[local] = path.substringAfterLast('.')
        }
        return result
    }

    /** Table simple names declared in this file (same-package tables need no import). */
    fun declaredTableNames(file: KtFile): Set<String> =
        file.collect<KtClassOrObject>().mapNotNullTo(mutableSetOf()) {
            it.name?.takeIf { name -> name.endsWith("Table") }
        }

    private fun resolveTable(
        name: String,
        imports: Map<String, String>,
        visibleTables: Set<String>,
        qualifiedRoot: String?,
    ): String? =
        imports[name] ?: visibleTables.takeIf { name in it }?.let { name }
            ?: name.takeIf { it.endsWith("Table") && qualifiedRoot?.startsWith("$BASE_PACKAGE.") == true }

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

    private val TABLE_WRITE_OPS = setOf("insert", "insertIgnore", "update", "deleteWhere", "upsert")

    /**
     * `Table.writeOp(...)` sites as `Table.op`. Receiver resolves through
     * aliases, same-package declarations and qualified paths, like [tableLeaks].
     */
    fun tableWriteOps(
        file: KtFile,
        visibleTables: Set<String>,
    ): List<String> {
        val imports = ourTableImports(file)
        return file
            .collect<KtCallExpression>()
            .filter { isCallTo(it, TABLE_WRITE_OPS) }
            .mapNotNull { call ->
                val receiver = (call.parent as? KtDotQualifiedExpression)?.receiverExpression ?: return@mapNotNull null
                val base =
                    when (receiver) {
                        is KtSimpleNameExpression -> receiver.getReferencedName()
                        is KtDotQualifiedExpression -> receiver.text.substringAfterLast('.').substringBefore('<')
                        else -> return@mapNotNull null
                    }
                val table = resolveTable(base, imports, visibleTables, outermostQualifiedText(receiver))
                table?.let { "$it.${calleeName(call)}" }
            }.distinct()
    }

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

    private fun isSharedStoreOwner(owner: String): Boolean = owner == LEGACY_SHARED

    private fun isForeignStore(
        store: String,
        importerOwner: String,
        storeOwners: Map<String, Set<String>>,
    ): Boolean = storeOwners[store]?.any { it != importerOwner && !isSharedStoreOwner(it) } == true

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
     * derivation); [store] imports from [LEGACY_SHARED] stay allowed until
     * their move. [allowed] carries explicit read-projection grants.
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

    // ---- Clock ownership (PSI call shapes; prose never matches) ----

    fun containsManilaZone(file: KtFile): Boolean = callSites(file, "ZoneId", "of") > 0

    fun containsIndependentToday(file: KtFile): Boolean =
        callSites(file, "LocalDate", "now") + callSites(file, "OffsetDateTime", "now") > 0

    fun containsInstantNow(file: KtFile): Boolean = callSites(file, "Instant", "now") > 0
}
