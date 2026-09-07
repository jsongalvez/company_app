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
) {
    // #532 deliberate ByteArray equality: data-class generated equals/hashCode
    // would compare array identity, so export payloads compare by content.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportResult) return false
        return bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            fileName == other.fileName
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = HASH_FACTOR * result + contentType.hashCode()
        result = HASH_FACTOR * result + fileName.hashCode()
        return result
    }

    private companion object {
        const val HASH_FACTOR = 31
    }
}
