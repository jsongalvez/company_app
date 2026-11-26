package com.companyb.companyapp.logging

/**
 * The tables one statement's text reads (a write reads its own table), writes and deletes from,
 * before [SchemaLinks] resolves views and cascades. [UNNAMED] stands for every table where the text
 * can't say: a batch, a function in a `FROM`, a statement kind this does not parse, or a write whose
 * target it can't name.
 */
internal data class StatementTables(
    val reads: Set<String>,
    val writes: Set<String>,
    val deletes: Set<String>,
) {
    companion object {
        const val UNNAMED = "*"

        private val quotedLiteral = Regex("'(?:[^']|'')*'")
        private val kindWord = Regex("^[\\s(]*(\\w+)")
        private val settings = setOf("SET", "SHOW", "RESET")
        private val parsed = setOf("SELECT", "WITH", "INSERT", "UPDATE", "DELETE")
        private val writeTarget =
            Regex("^\\s*(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(\\w+)[\\s(]", RegexOption.IGNORE_CASE)

        /** `FROM`, `JOIN` or `USING`, but not `IS DISTINCT FROM` or the `FROM` in `EXTRACT(YEAR FROM x)`. */
        private val source =
            Regex(
                "(?<!\\bDISTINCT\\s{1,8})(?<!\\b(?:EXTRACT|SUBSTRING|TRIM|OVERLAY)\\s{0,8}\\([^()]{1,64})" +
                    "\\b(?:FROM|JOIN|USING)\\s+",
                RegexOption.IGNORE_CASE,
            )
        private val writeAnywhere =
            Regex("\\b(?:INSERT\\s+INTO\\s+(\\w+)|UPDATE\\s+(\\w+)\\s+SET)\\b", RegexOption.IGNORE_CASE)
        private val table = Regex("\\G(?:\\w+\\.)?(\\w+)(\\s*\\()?")
        private val alias = Regex("\\G\\s+(?:AS\\s+)?\\w+", RegexOption.IGNORE_CASE)
        private val comma = Regex("\\G\\s*,\\s*")
        private val cte =
            Regex(
                "(?:\\bWITH(?:\\s+RECURSIVE)?|,)\\s+(\\w+)\\s+AS\\s+(?:NOT\\s+)?(?:MATERIALIZED\\s+)?\\(",
                RegexOption.IGNORE_CASE,
            )

        fun of(sql: String): StatementTables {
            val text = sql.replace(quotedLiteral, "''").replace("\"", "")
            val kind =
                kindWord
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.uppercase()
            val target = writeTarget.find(text)
            val writes =
                when {
                    kind in settings || kind == "SELECT" -> emptySet()
                    target != null -> setOf(target.groupValues[2])
                    else -> setOf(UNNAMED)
                }
            val deletes =
                if (target != null && target.groupValues[1].uppercase().startsWith("DELETE")) writes else emptySet()
            val reads =
                when (kind) {
                    in settings -> emptySet()
                    in parsed -> namedReads(text, kind == "WITH")
                    else -> setOf(UNNAMED)
                }
            return StatementTables(reads, writes, deletes)
        }

        private fun namedReads(
            text: String,
            withCtes: Boolean,
        ): Set<String> {
            val named = source.findAll(text).map { sourcesAt(text, it.range.last + 1) }.toList()
            if (named.any { it == null }) return setOf(UNNAMED)
            val ctes = if (withCtes) cte.findAll(text).map { it.groupValues[1] }.toSet() else emptySet()
            val written = writeAnywhere.findAll(text).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            return named.flatMap { it.orEmpty() }.toSet() + written - ctes
        }

        /** The tables in the `FROM` list starting at [start]; null when one of them is not a table's name. */
        private fun sourcesAt(
            text: String,
            start: Int,
        ): List<String>? {
            val names = mutableListOf<String>()
            var at: Int? = start
            while (at != null) {
                val end = sourceEnd(text, at, names) ?: return null
                val afterAlias = alias.find(text, end)?.let { it.range.last + 1 } ?: end
                at = comma.find(text, afterAlias)?.let { it.range.last + 1 }
            }
            return names
        }

        /** Where the source at [at] ends, adding its table to [names]; null when it is not a table's name. */
        private fun sourceEnd(
            text: String,
            at: Int,
            names: MutableList<String>,
        ): Int? =
            if (text.getOrNull(at) == '(') {
                closingParen(text, at)?.plus(1)
            } else {
                table.find(text, at)?.takeIf { it.groupValues[2].isEmpty() }?.let {
                    names += it.groupValues[1]
                    it.range.last + 1
                }
            }

        private fun closingParen(
            text: String,
            open: Int,
        ): Int? {
            var depth = 0
            for (i in open until text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) return i
                }
            }
            return null
        }
    }
}
