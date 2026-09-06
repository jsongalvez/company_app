package com.companyb.companyapp.repository

object AuditValues {
    const val NULL = "null"
    const val NOW = "now"
    const val NOW_FN = "now()"

    /**
     * Anonymization redaction marker (#524): replaces identifying values in
     * retained client audit payloads. Uniform across every anonymized record,
     * so the marker itself carries no recoverable identity. Distinct from
     * [NULL] so cleared-state history keeps its shape for #525.
     */
    const val REDACTED = "[redacted]"
}
