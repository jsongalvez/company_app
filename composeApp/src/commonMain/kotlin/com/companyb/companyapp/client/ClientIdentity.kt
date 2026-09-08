package com.companyb.companyapp.client

import com.companyb.companyapp.contracts.client.ClientResponse

/**
 * #673 — one client identity presentation for directory, intake picker and sale picker.
 *
 * Primary: full name; secondary: phone; age/address only to disambiguate same-name rows.
 * Never clinical concerns (medicalConditions, BP) or raw IDs in search rows. Missing values
 * read as an em dash; anonymized records read as "Anonymized client" and keep existing
 * selection behavior (no new restriction introduced here).
 */
const val CLIENT_MISSING_VALUE = "—"
const val CLIENT_ANONYMIZED_LABEL = "Anonymized client"

internal fun isAnonymizedClient(client: ClientResponse): Boolean = client.firstName == null || client.lastName == null

internal fun clientPrimaryName(client: ClientResponse): String {
    if (isAnonymizedClient(client)) return CLIENT_ANONYMIZED_LABEL
    return clientDisplayName(client).ifBlank { CLIENT_MISSING_VALUE }
}

internal fun clientPhoneLine(client: ClientResponse): String =
    client.phoneNumber?.takeIf { it.isNotBlank() } ?: CLIENT_MISSING_VALUE

/**
 * Disambiguator for same-name rows: always "Age X" plus address when present (age is a
 * non-null Int, so this is never null for non-anonymized clients). Callers show it only
 * when the sibling list holds a duplicate primary name (#673 "as needed").
 */
internal fun clientDisambiguator(client: ClientResponse): String? {
    if (isAnonymizedClient(client)) return null
    val parts = mutableListOf<String>()
    parts.add("Age ${client.age}")
    client.address?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    return parts.joinToString(" · ").takeIf { parts.isNotEmpty() }
}

internal fun needsDisambiguation(
    client: ClientResponse,
    siblings: List<ClientResponse>,
): Boolean {
    if (isAnonymizedClient(client)) return false
    val primary = clientPrimaryName(client)
    return siblings.count { !isAnonymizedClient(it) && clientPrimaryName(it) == primary } > 1
}

internal fun clientSecondaryLine(
    client: ClientResponse,
    siblings: List<ClientResponse> = emptyList(),
): String {
    if (isAnonymizedClient(client)) return CLIENT_MISSING_VALUE
    val phone = clientPhoneLine(client)
    if (!needsDisambiguation(client, siblings)) return phone
    val extra = clientDisambiguator(client)
    return if (extra == null) phone else "$phone · $extra"
}

/**
 * Pure keyboard-navigation decision for picker lists (#673): arrow keys move focus,
 * Enter selects the focused row, Tab reaches explicit Create client. No default selection
 * on a response landing (focused index starts at -1).
 */
internal fun movePickerFocus(
    current: Int,
    direction: Int,
    size: Int,
): Int {
    if (size <= 0) return -1
    if (current < 0) return if (direction > 0) 0 else size - 1
    return (current + direction).coerceIn(0, size - 1)
}
