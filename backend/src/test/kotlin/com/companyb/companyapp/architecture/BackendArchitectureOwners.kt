package com.companyb.companyapp.architecture

import java.io.File

/**
 * Semantic owner/seam policy for map #533 (#534).
 *
 * Single architecture-test owner: whole-source discovery plus small explicit
 * owner mapping that supports legacy locations and #533 target packages side
 * by side. Production movement belongs to the feature children (#536, #537);
 * this file moves no production sources.
 *
 * Owner vocabulary follows #533: identity, authorization, branch, branchday,
 * workforce, client, session, commerce, commission, remittance, reporting,
 * audit, notification; mechanisms http, database, observability, logging;
 * legacy buckets persistence (global repository layer) and mechanism/app.
 */
object BackendArchitectureOwners {
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
            "service/finance/commission/" to "commission",
            "service/commission/" to "commission",
            "commission/" to "commission",
            "service/finance/remittance/" to "remittance",
            "service/remittance/" to "remittance",
            "remittance/" to "remittance",
            "service/dashboard/" to "reporting",
            "service/export/" to "reporting",
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

    /** Files allowed to hold a direct BranchDay store import; tightened by #536. */
    val branchDayStoreReaders: Set<String> =
        setOf(
            "service/ReliefInviteService.kt",
            "service/attendance/AttendanceService.kt",
            "service/attendance/ShiftGuard.kt",
            "service/dashboard/DashboardService.kt",
            "service/finance/commission/CommissionService.kt",
            "service/finance/remittance/RemittanceService.kt",
        )

    /** Recorded Instant.now owners (#322, retained): auth lifecycle, branch-day clock, incident filing. */
    val instantOwners: Set<String> =
        setOf(
            "auth/JwtService.kt",
            "observability/IncidentService.kt",
            "service/AuthService.kt",
            "service/UserService.kt",
            "service/branchday/BranchDayService.kt",
        )

    /**
     * Strips line/block comments and string/char literals so doc prose can no
     * longer satisfy or dodge code predicates (#412 shape, shared here).
     */
    fun codeOnly(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    out.append('\n')
                    i = source.indexOf('\n', i).takeIf { it >= 0 } ?: source.length
                }

                source.startsWith("/*", i) -> {
                    i = skipBlockComment(source, i, out)
                }

                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3).takeIf { it >= 0 }?.plus(3) ?: source.length
                    out.append('\n'.toString().repeat(source.substring(i, end).count { it == '\n' }))
                    out.append(' ')
                    i = end
                }

                source[i] == '"' || source[i] == '\'' -> {
                    i = skipQuoted(source, i, out)
                }

                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun skipBlockComment(
        source: String,
        start: Int,
        out: StringBuilder,
    ): Int {
        var depth = 1
        var i = start + 2
        while (i < source.length && depth > 0) {
            when {
                source.startsWith("*/", i) -> {
                    depth--
                    i += 2
                }

                source.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }

                else -> {
                    if (source[i] == '\n') out.append('\n')
                    i++
                }
            }
        }
        out.append(' ')
        return i
    }

    private fun skipQuoted(
        source: String,
        start: Int,
        out: StringBuilder,
    ): Int {
        var i = start + 1
        while (i < source.length && source[i] != source[start]) {
            if (source[i] == '\\') i++
            i++
        }
        out.append(' ')
        return (i + 1).coerceAtMost(source.length)
    }

    /** Local name to persistence table for repository.model imports, including `as` aliases. */
    fun tableImports(source: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in IMPORT_LINE.findAll(source)) {
            val path = match.groupValues[1]
            val alias = match.groupValues[2].ifEmpty { null }
            val table = PERSISTENCE_TABLE_IMPORT.find(path)?.groupValues?.get(1) ?: continue
            result[alias ?: table] = table
        }
        return result
    }

    /**
     * Declaration-aware table-leak check: a persistence-table reference is
     * allowed only inside an `internal` type body. Replaces the
     * end-of-first-object heuristic, which missed leaks after a preceding
     * seam, and honors import aliases.
     */
    fun tableLeaks(source: String): List<String> {
        val imports = tableImports(source)
        if (imports.isEmpty()) return emptyList()
        val code = codeOnly(withoutImportsAndPackage(source))
        val seams = internalRanges(code)
        val byTable = mutableMapOf<String, MutableSet<String>>()
        for ((local, table) in imports) byTable.getOrPut(table) { mutableSetOf() }.add(local)
        val leaked = mutableListOf<String>()
        for ((table, locals) in byTable) {
            val refs = Regex("\\b(${locals.joinToString("|") { Regex.escape(it) }})\\b").findAll(code)
            if (refs.any { hit -> seams.none { hit.range.first in it } }) leaked.add(table)
        }
        return leaked
    }

    private fun withoutImportsAndPackage(source: String): String =
        source
            .replace(IMPORT_LINE, "")
            .replace(PACKAGE_LINE, "")

    /** Brace ranges of `internal` type bodies in comment/string-stripped code. */
    fun internalRanges(code: String): List<IntRange> =
        TYPE_DECLARATION
            .findAll(code)
            .mapNotNull { header ->
                if ("internal" !in header.groupValues[1].split(Regex("\\s+"))) return@mapNotNull null
                val bodyStart = nextScopeBrace(code, header.range.last + 1) ?: return@mapNotNull null
                val bodyEnd = matchBrace(code, bodyStart) ?: return@mapNotNull null
                bodyStart..bodyEnd
            }.toList()

    private fun nextScopeBrace(
        code: String,
        from: Int,
    ): Int? {
        val brace = code.indexOf('{', from)
        val window = if (brace < 0) "" else code.substring(from, brace)
        val cleanWindow = !TYPE_DECLARATION.containsMatchIn(window) && !FUN_DECLARATION.containsMatchIn(window)
        return if (brace >= 0 && cleanWindow) brace else null
    }

    private fun matchBrace(
        code: String,
        open: Int,
    ): Int? {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> {
                    depth++
                }

                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    /** HTTP-adapter rule: no Exposed, transaction, persistence-table, or raw-exec shapes. */
    fun apiLayerViolations(source: String): List<String> =
        buildList {
            if (EXPOSED_IMPORT.containsMatchIn(source)) add("exposed-import")
            if (TRANSACTION_BLOCK.containsMatchIn(codeOnly(source))) add("transaction-block")
            if (TABLE_IMPORT.containsMatchIn(source)) add("persistence-table-import")
            if (RAW_EXEC.containsMatchIn(codeOnly(source))) add("raw-sql-exec")
        }

    /** Top-level store declarations missing the `internal` marker (service scope). */
    fun nonInternalStoreDeclarations(source: String): List<String> =
        PUBLIC_STORE_DECLARATION.findAll(codeOnly(source)).map { it.value }.toList()

    fun containsAuditRecordCall(source: String): Boolean = AUDIT_RECORD_CALL.containsMatchIn(codeOnly(source))

    fun containsAuditFn(source: String): Boolean = "auditFn" in codeOnly(source)

    /** Highest transaction-block count inside any single function body; commands own at most one. */
    fun maxTransactionsPerFunction(source: String): Int {
        val code = "\n" + codeOnly(source)
        val starts = FUN_DECLARATION.findAll(code).map { it.range.first }.toList() + code.length
        return starts
            .zipWithNext { start, next -> code.substring(start, next) }
            .map { body -> TRANSACTION_BLOCK.findAll(body).count() }
            .maxOrNull() ?: 0
    }

    /** In-transaction stores must open no transaction of their own (ADR-0024). */
    fun inTransactionFunctionsWithNestedTransaction(source: String): List<String> {
        val code = "\n" + codeOnly(source)
        val starts = FUN_DECLARATION.findAll(code).toList()
        return starts
            .mapIndexed { index, match ->
                val end = starts.getOrNull(index + 1)?.range?.first ?: code.length
                match.groupValues[1] to code.substring(match.range.first, end)
            }.filter { (name, _) -> IN_TRANSACTION_FUN in name }
            .filter { (_, body) -> TRANSACTION_BLOCK.containsMatchIn(body) }
            .map { (name, _) -> name }
            .toList()
    }

    /** BranchDay write operations; allowed only in the branchday owner. */
    fun branchDayTableWrites(source: String): List<String> =
        BRANCH_DAY_WRITE
            .findAll(codeOnly(source))
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    fun importsBranchDayStore(source: String): Boolean = BRANCH_DAY_STORE_IMPORT.containsMatchIn(source)

    fun containsManilaZone(source: String): Boolean = MANILA_ZONE in codeOnly(source)

    fun containsIndependentToday(source: String): Boolean =
        codeOnly(source).let { it.contains("LocalDate.now") || it.contains("OffsetDateTime.now") }

    fun containsInstantNow(source: String): Boolean = "Instant.now()" in codeOnly(source)

    private val IMPORT_LINE = Regex("(?m)^\\s*import\\s+(\\S+?)(?:\\s+as\\s+(\\w+))?\\s*$")
    private val PACKAGE_LINE = Regex("(?m)^\\s*package\\s+.*$")
    private val TYPE_DECLARATION =
        Regex(
            "(?m)^\\s*((?:(?:private|internal|protected|public|open|data|enum|sealed|abstract)\\s+)*)" +
                "(object|class|interface)\\s+\\w+",
        )
    private val FUN_DECLARATION =
        Regex("\n\\s*(?:(?:private|internal|protected|public)\\s+)?(?:suspend\\s+)?fun\\s+(\\w+)")
    private val EXPOSED_IMPORT = Regex("import org\\.jetbrains\\.exposed")
    private val TRANSACTION_BLOCK = Regex("\\btransaction\\s*[({]")
    private val TABLE_IMPORT = Regex("import com\\.companyb\\.companyapp\\.repository\\.model\\.\\w*Table")
    private val PERSISTENCE_TABLE_IMPORT = Regex("com\\.companyb\\.companyapp\\.repository\\.model\\.(\\w*Table)")
    private val RAW_EXEC = Regex("\\bexec\\s*\\(")
    private val PUBLIC_STORE_DECLARATION = Regex("(?m)^(?:object|class) \\w*(?:Repository|Store)\\b")
    private val AUDIT_RECORD_CALL = Regex("\\bAuditLogRepository\\.record\\w*\\(")
    private val BRANCH_DAY_WRITE = Regex("BranchDayTable\\.(update|insert|insertIgnore|deleteWhere|upsert)")
    private val BRANCH_DAY_STORE_IMPORT =
        Regex("import com\\.companyb\\.companyapp\\.(service\\.branchday|branchday)\\.BranchDayRepository")
    private const val MANILA_ZONE = "ZoneId.of("
    private const val IN_TRANSACTION_FUN = "InTransaction"
}
