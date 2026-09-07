package com.companyb.detekt.deadcode

import java.io.File

private const val EXIT_OK = 0
private const val EXIT_GATE_FAILURE = 1
private const val EXIT_TOOL_ERROR = 2

// CLI driver for #530: the same authoritative gate CI runs. Local reproduction
// is `./gradlew deadCodeCheck`; CI invokes the identical task and baseline.
fun main(args: Array<String>) {
    kotlin.system.exitProcess(DeadCodeMain.run(args))
}

object DeadCodeMain {
    fun run(args: Array<String>): Int =
        try {
            execute(args)
        } catch (usage: DeadCodeUsageException) {
            System.err.println(usage.message)
            EXIT_TOOL_ERROR
        }

    private fun execute(args: Array<String>): Int {
        val parsed = parse(args) ?: return EXIT_TOOL_ERROR
        val candidates = collectSources(parsed.candidateRoots)
        if (candidates.isEmpty()) {
            System.err.println("deadcode: no Kotlin sources under candidate roots ${parsed.candidateRoots}")
            return EXIT_TOOL_ERROR
        }
        return analyzeAndGate(parsed, candidates, collectSources(parsed.consumerRoots))
    }

    private fun analyzeAndGate(
        parsed: Parsed,
        candidates: List<File>,
        consumers: List<File>,
    ): Int {
        val result = DeadCodeAnalyzer.analyze(parsed.root, candidates, consumers, parsed.classpathOrDiscovered())
        reportHealth(result)
        if (parsed.writeBaseline != null) {
            parsed.writeBaseline.writeText(DeadCodeBaseline.format(result.findings))
            println("deadcode: wrote ${result.findings.size} findings to ${parsed.writeBaseline}")
            return EXIT_OK
        }
        if (parsed.reportInventory) {
            printInventory(result.findings)
        }
        val baselined = DeadCodeBaseline.load(parsed.baseline)
        val exempt = parsed.entryPoints?.let { DeadCodeBaseline.load(it) } ?: emptySet()
        println("deadcode: ${result.findings.size} findings (${baselined.size} baselined, ${exempt.size} exempt)")
        val diff = DeadCodeBaseline.diff(result.findings, baselined + exempt)
        val unanalyzed = result.failedFiles - parsed.knownUnanalyzed
        val analyzedNow = parsed.knownUnanalyzed - result.failedFiles.toSet()
        return reportGate(diff, unanalyzed, analyzedNow)
    }

    private data class Parsed(
        val root: File,
        val candidateRoots: List<File>,
        val consumerRoots: List<File>,
        val targetClasspath: List<File>,
        val baseline: File,
        val entryPoints: File?,
        val writeBaseline: File?,
        val knownUnanalyzed: Set<String>,
        val reportInventory: Boolean,
    ) {
        // Fixture/test mode passes no --classpath; the real gate always does.
        // Fall back to the tool's own stdlib so bare snippets still bind.
        fun classpathOrDiscovered(): List<File> = targetClasspath.ifEmpty { discoverStdlib() }
    }

    private class MutableRoots {
        val candidates = mutableListOf<String>()
        val consumers = mutableListOf<String>()
        val targetClasspath = mutableListOf<String>()
    }

    private class MutableParse {
        val roots = MutableRoots()
        var root: File = File(System.getProperty("user.dir"))
        var baseline: String? = null
        var entryPoints: String? = null
        var writeBaseline: String? = null
        var knownUnanalyzed: String? = null
        var reportInventory: Boolean = false
        var error: String? = null
    }

    private fun parse(args: Array<String>): Parsed? {
        val state = MutableParse()
        var index = 0
        while (index < args.size && state.error == null) {
            index = consumeFlag(args, index, state)
        }
        if (state.error == null && state.baseline == null) {
            state.error = "--baseline <file> is required"
        }
        if (state.error != null) {
            System.err.println("deadcode: ${state.error}")
            return null
        }
        return Parsed(
            root = state.root.absoluteFile,
            candidateRoots = state.roots.candidates.map { File(it).absoluteFile },
            consumerRoots = state.roots.consumers.map { File(it).absoluteFile },
            targetClasspath = state.roots.targetClasspath.map { File(it) },
            baseline = File(requireNotNull(state.baseline) { "baseline missing" }),
            entryPoints = state.entryPoints?.let { File(it) },
            writeBaseline = state.writeBaseline?.let { File(it) },
            knownUnanalyzed = state.knownUnanalyzed?.let { DeadCodeBaseline.load(File(it)) } ?: emptySet(),
            reportInventory = state.reportInventory,
        )
    }

    private fun consumeFlag(
        args: Array<String>,
        index: Int,
        state: MutableParse,
    ): Int {
        when (args[index]) {
            "--candidate" -> {
                state.roots.candidates.add(flagValue(args, index))
            }

            "--consumer" -> {
                state.roots.consumers.add(flagValue(args, index))
            }

            "--classpath" -> {
                state.roots.targetClasspath.add(flagValue(args, index))
            }

            "--root" -> {
                state.root = File(flagValue(args, index))
            }

            "--baseline" -> {
                state.baseline = flagValue(args, index)
            }

            "--entry-points" -> {
                state.entryPoints = flagValue(args, index)
            }

            "--write-baseline" -> {
                state.writeBaseline = flagValue(args, index)
            }

            "--known-unanalyzed" -> {
                state.knownUnanalyzed = flagValue(args, index)
            }

            "--report-inventory" -> {
                state.reportInventory = true
                return index + 1
            }

            else -> {
                state.error = "unknown argument ${args[index]}"
                return index + 1
            }
        }
        return index + 2
    }

    private fun flagValue(
        args: Array<String>,
        index: Int,
    ): String {
        if (index + 1 >= args.size) throw DeadCodeUsageException("deadcode: ${args[index]} needs a value")
        return args[index + 1]
    }

    private fun collectSources(roots: List<File>): List<File> =
        roots
            .flatMap { root ->
                if (!root.exists()) {
                    System.err.println("deadcode: source root missing, skipped: $root")
                    emptyList()
                } else {
                    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                }
            }.distinct()
            .sorted()

    private fun discoverStdlib(): List<File> {
        val found =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter { it.endsWith(".jar") }
                .map { File(it) }
                .filter { it.name.startsWith("kotlin-stdlib-") && it.exists() }
                .filter { !it.name.contains("sources") && !it.name.contains("javadoc") }
        if (found.isEmpty()) {
            System.err.println("deadcode: no kotlin-stdlib on the tool classpath; analyzing without a stdlib")
        }
        return found
    }

    private fun reportHealth(result: DeadCodeResult) {
        println("deadcode: analyzed ${result.analyzedFiles} files, ${result.findings.size} findings")
        if (result.unresolvedDeclarations > 0) {
            println("deadcode: ${result.unresolvedDeclarations} declarations had no descriptor; kept live")
        }
        for (failed in result.failedFiles) {
            System.err.println("deadcode: syntax errors, excluded from candidates: $failed")
        }
    }

    private fun printInventory(findings: List<DeadCodeFinding>) {
        val grouped = findings.groupBy { it.path.substringBeforeLast('/') }
        println("deadcode: inventory by owning directory (${findings.size} findings)")
        for ((directory, entries) in grouped.toSortedMap()) {
            println("## $directory (${entries.size})")
            for (entry in entries.sortedBy { it.key() }) {
                println("- ${entry.kind} ${entry.signature}")
            }
        }
    }

    private fun reportGate(
        diff: BaselineDiff,
        unanalyzed: List<String>,
        analyzedNow: Set<String>,
    ): Int {
        for (finding in diff.unbaselined) {
            println("deadcode: NEW ${finding.key()} (line ${finding.line})")
        }
        for (stale in diff.stale) {
            println("deadcode: STALE $stale")
        }
        for (path in unanalyzed) {
            println("deadcode: UNANALYZED $path")
        }
        for (path in analyzedNow.sorted()) {
            println("deadcode: ANALYZED-NOW $path")
        }
        if (!diff.isClean() || unanalyzed.isNotEmpty() || analyzedNow.isNotEmpty()) {
            println("deadcode: gate FAILED")
            return EXIT_GATE_FAILURE
        }
        println("deadcode: gate PASSED")
        return EXIT_OK
    }
}

private class DeadCodeUsageException(
    message: String,
) : IllegalArgumentException(message)
