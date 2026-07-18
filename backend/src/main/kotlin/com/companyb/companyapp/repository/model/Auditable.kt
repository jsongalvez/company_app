package com.companyb.companyapp.repository.model

import java.util.UUID

interface Auditable {
    val id: UUID

    fun toAuditFields(): Map<String, String>
}
