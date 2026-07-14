package com.companyb.companyapp.service.export

enum class ExportFormat(
    val contentType: String,
    val extension: String,
) {
    CSV("text/csv", "csv"),
    PDF("application/pdf", "pdf"),
}
