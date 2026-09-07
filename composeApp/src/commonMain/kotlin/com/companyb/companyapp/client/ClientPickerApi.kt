package com.companyb.companyapp.client

import com.companyb.companyapp.dto.ClientResponse

/**
 * #558 — the narrow client-owned picker boundary: search + select only, never the
 * session form draft/preview/submit surface. Session creation consumes this seam
 * (its `SessionClientPickerApi` extends it); product-sale consumes [ClientViewModel]
 * directly as the deliberate linked-client edge.
 */
interface ClientPickerApi {
    val onQueryChange: (String) -> Unit
    val retrySearch: () -> Unit

    fun selectClient(client: ClientResponse)
}
