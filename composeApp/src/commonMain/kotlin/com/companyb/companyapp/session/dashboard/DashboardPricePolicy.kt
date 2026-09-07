package com.companyb.companyapp.session.dashboard

// #556 — the dashboard price equality/validation policy (#149/#135 mirrors), colocated in
// session/dashboard beside DashboardEditState as pure adjacent policy.

/**
 * Semantic price equality: "2750" == "2750.00" == "2750.0" (the backend normalizes to
 * plain strings of varying scale; the draft is raw input).
 */
internal fun pricesEqual(
    a: String,
    b: String,
): Boolean = normalizePrice(a) == normalizePrice(b)

internal fun normalizePrice(raw: String): String {
    var value = raw.trim()
    if ('.' in value) {
        value = value.trimEnd('0').trimEnd('.')
    }
    return value
}

/**
 * Client mirror of the backend's price validation (parseNonNegativeBigDecimal): blank /
 * negative / non-numeric drafts are rejected before dispatch (the #135 parseSlotInput
 * mirror pattern — a rejected draft gets the inline error instead of a wire 400).
 * Values with >2 decimals pass (the backend rounds them server-side, authoritative).
 */
internal fun finalPriceInputValid(raw: String): Boolean = raw.trim().matches(Regex("""\d+(\.\d+)?"""))

internal fun fieldValuesEqual(
    a: String,
    b: String,
    field: DashboardEditField,
): Boolean = if (field == DashboardEditField.FINAL_PRICE) pricesEqual(a, b) else a == b
