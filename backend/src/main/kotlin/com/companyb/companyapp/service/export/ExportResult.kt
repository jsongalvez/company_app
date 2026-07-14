package com.companyb.companyapp.service.export

data class ExportResult(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
)
