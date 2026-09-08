package com.companyb.detekt.deadcode

import java.io.File

data class ExemptionsDiff(
    val unexempted: List<DeadCodeFinding>,
    val stale: List<String>,
) {
    fun isClean(): Boolean = unexempted.isEmpty() && stale.isEmpty()
}

// Exemption sets for the zero-debt gate (map #529 Phase C): concrete finding
// keys, one per line, never wildcards or category suppressions. Backs the
// permanent narrow entry-points file and the known-unanalyzed set. There is
// no migration baseline and no write path — new dead declarations fail.
object DeadCodeExemptions {
    // A trailing " # reason" comment is allowed (required by convention in
    // entry-points) and ignored by the loader.
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
        exempted: Set<String>,
    ): ExemptionsDiff {
        val keys = findings.map { it.key() }.toSet()
        return ExemptionsDiff(
            unexempted = findings.filter { it.key() !in exempted },
            stale = (exempted - keys).sorted(),
        )
    }
}
