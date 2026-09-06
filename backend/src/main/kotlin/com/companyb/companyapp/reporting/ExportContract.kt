package com.companyb.companyapp.reporting

enum class ExportFormat(
    val contentType: String,
    val extension: String,
) {
    CSV("text/csv", "csv"),
    PDF("application/pdf", "pdf"),
}

data class ExportResult(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
)
