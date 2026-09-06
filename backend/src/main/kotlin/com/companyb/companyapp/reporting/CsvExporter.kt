package com.companyb.companyapp.reporting

/**
 * Cell representation for spreadsheet-safe CSV encoding (#520).
 *
 * Numeric cells hold server-computed amounts/counts and emit verbatim to preserve
 * numeric type. Text cells hold untrusted/persisted text (branch names, month names,
 * headers) and gain OWASP single-quote neutralization when they look like formulas.
 */
sealed interface CsvCell {
    val value: String

    data class Text(
        override val value: String,
    ) : CsvCell

    data class Numeric(
        override val value: String,
    ) : CsvCell
}

/**
 * Minimal RFC-4180 encoder with formula-injection hardening.
 *
 * Text cells starting (after leading spaces) with `= + - @ TAB CR LF` gain a leading
 * single quote (OWASP CSV Formula Injection text-marker convention) so spreadsheet
 * applications render them as literal text. Trusted [CsvCell.Numeric] cells bypass the
 * prefix so negative monetary amounts stay numeric. Fields containing `, " CR LF`
 * are double-quoted with embedded quotes doubled.
 */
object CsvExporter {
    private const val TEXT_MARKER = "'"
    private const val FORMULA_TRIGGERS = "=+-@\t\r\n"
    private const val QUOTE_CHARS = ",\"\r\n"

    private val plainNumber = Regex("^-?\\d+(\\.\\d+)?$")

    fun generate(
        headers: List<String>,
        rows: List<List<CsvCell>>,
    ): ByteArray {
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { encodeText(it) })
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { encodeCell(it) })
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    internal fun encodeCell(cell: CsvCell): String =
        when (cell) {
            is CsvCell.Numeric -> if (plainNumber.matches(cell.value)) cell.value else encodeText(cell.value)
            is CsvCell.Text -> encodeText(cell.value)
        }

    internal fun encodeText(field: String): String {
        val safe = if (needsFormulaPrefix(field)) TEXT_MARKER + field else field
        return if (safe.any { it in QUOTE_CHARS }) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }

    private fun needsFormulaPrefix(field: String): Boolean {
        val trimmed = field.trimStart(' ')
        return trimmed.isNotEmpty() && trimmed[0] in FORMULA_TRIGGERS
    }
}
