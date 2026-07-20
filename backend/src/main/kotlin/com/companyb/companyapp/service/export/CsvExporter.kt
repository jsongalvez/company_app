package com.companyb.companyapp.service.export

object CsvExporter {
    fun generate(
        headers: List<String>,
        rows: List<List<String>>,
    ): ByteArray {
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { escapeCsvField(it) })
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escapeCsvField(it) })
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escapeCsvField(field: String): String =
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
}
