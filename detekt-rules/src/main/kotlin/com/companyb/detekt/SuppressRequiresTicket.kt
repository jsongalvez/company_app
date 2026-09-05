package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

class SuppressRequiresTicket(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Maintainability,
            "Bare @Suppress without a #<ticket> reference on the same or preceding line.",
            Debt.FIVE_MINS,
        )

    private val grandfather: List<GrandfatheredSuppression> by lazy { loadGrandfather() }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        if (annotationEntry.calleeExpression?.text == SUPPRESS_ANNOTATION) {
            checkSuppressAnnotation(annotationEntry)
        }
    }

    private fun checkSuppressAnnotation(annotationEntry: KtAnnotationEntry) {
        val file = annotationEntry.containingFile as? KtFile
        if (file != null && !hasTicketReference(file, annotationEntry)) {
            val uncovered = uncoveredIds(filePathOf(file), suppressedIdsIn(annotationEntry))
            if (uncovered.isNotEmpty()) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(annotationEntry),
                        "Bare @Suppress(${uncovered.joinToString()}) " +
                            "requires a #<ticket> comment on the same or preceding line (ref #465).",
                    ),
                )
            }
        }
    }

    internal fun uncoveredIds(
        filePath: String,
        ids: List<String>,
    ): List<String> = ids.filter { id -> grandfather.none { entry -> entry.matches(filePath, id) } }

    private fun suppressedIdsIn(annotationEntry: KtAnnotationEntry): List<String> {
        val ids = SUPPRESSED_ID.findAll(annotationEntry.text).map { it.groupValues[1] }.toList()
        return ids.ifEmpty { listOf(UNPARSEABLE_ARGUMENT) }
    }

    private fun hasTicketReference(
        file: KtFile,
        annotationEntry: KtAnnotationEntry,
    ): Boolean {
        val lines = file.text.split('\n')
        val index = file.text.take(annotationEntry.textOffset).count { it == '\n' }
        return TICKET_REFERENCE.containsMatchIn(lines.getOrElse(index) { "" }) ||
            (index > 0 && TICKET_REFERENCE.containsMatchIn(lines.getOrElse(index - 1) { "" }))
    }

    private fun filePathOf(file: KtFile): String = file.virtualFile?.path ?: file.name

    private fun loadGrandfather(): List<GrandfatheredSuppression> =
        readGrandfatherLines().mapNotNull { GrandfatheredSuppression.parse(it) }

    private fun readGrandfatherLines(): List<String> {
        val stream = javaClass.getResourceAsStream(GRANDFATHER_RESOURCE)
        return if (stream == null) emptyList() else stream.bufferedReader().readLines()
    }

    companion object {
        private const val SUPPRESS_ANNOTATION = "Suppress"
        private const val UNPARSEABLE_ARGUMENT = "unparseable-suppress-argument"
        private const val GRANDFATHER_RESOURCE = "/suppression-grandfather.txt"
        private val SUPPRESSED_ID = Regex("\"([^\"\\\\]*)\"")
        private val TICKET_REFERENCE = Regex("#\\d+")
    }
}

internal data class GrandfatheredSuppression(
    val pathSuffix: String,
    val ruleId: String,
) {
    fun matches(
        filePath: String,
        ruleId: String,
    ): Boolean = this.ruleId == ruleId && filePath.endsWith(pathSuffix)

    companion object {
        fun parse(line: String): GrandfatheredSuppression? {
            val trimmed = line.trim()
            val separator = trimmed.lastIndexOf(':')
            return if (trimmed.isEmpty() || trimmed.startsWith("#") || separator <= 0) {
                null
            } else {
                GrandfatheredSuppression(trimmed.take(separator), trimmed.substring(separator + 1))
            }
        }
    }
}
