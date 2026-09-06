package com.companyb.companyapp.audit

object AuditValues {
    /**
     * Legacy null sentinel (#525): historical `{"k":"null"}` payloads encode
     * absent values as the string "null". New writes use JSON null instead;
     * readers must accept both. Do not emit this for new rows.
     */
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
