package com.companyb.detekt.deadcode

import java.io.File

data class BaselineDiff(
    val unbaselined: List<DeadCodeFinding>,
    val stale: List<String>,
) {
    fun isClean(): Boolean = unbaselined.isEmpty() && stale.isEmpty()
}

// Migration baseline for #529 Phase A: concrete finding keys, one
// per line, never wildcards or category suppressions. Cleanup children shrink
// it by deleting lines as declarations die; #529 deletes the file at zero.
object DeadCodeBaseline {
    // Both the migration baseline and the permanent narrow
    // entry-points file share this line format. A trailing " # reason"
    // comment is allowed (required by convention in entry-points) and ignored
    // by the loader; renderer output never contains " # ".
    fun load(file: File): Set<String> {
        if (!file.exists()) return emptySet()
        return file
            .readLines()
            .map { it.substringBefore(" # ").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    fun diff(
        findings: List<DeadCodeFinding>,
        baseline: Set<String>,
    ): BaselineDiff {
        val keys = findings.map { it.key() }.toSet()
        return BaselineDiff(
            unbaselined = findings.filter { it.key() !in baseline },
            stale = (baseline - keys).sorted(),
        )
    }

    fun format(findings: List<DeadCodeFinding>): String {
        val header =
            listOf(
                "# Dead-code grandfather baseline (map #529 Phase A, ref #530).",
                "# One concrete finding per line: <repo-relative path>|<kind>|<signature>.",
                "# No wildcards, no category suppressions. Cleanup children delete lines",
                "# as declarations die; the map deletes this file at zero debt.",
                "# Generated once via `./gradlew deadCodeCheck -PdeadCodeWriteBaseline=<this file>`;",
                "# every regeneration is a reviewed commit, never an ordinary feature-work step.",
            )
        return (header + findings.sortedBy { it.key() }.map { it.key() }).joinToString("\n", postfix = "\n")
    }
}
