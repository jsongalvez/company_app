package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.database.pgEnum
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.postgresql.util.PGobject

object ActiveUserCapabilitiesView : Table("active_user_capabilities") {
    val userId = javaUUID("user_id")
    val capabilityId = javaUUID("capability_id")
    val contextType =
        pgEnum("context_type", "capability_context_type", CapabilityContextType.entries)
    val contextId = javaUUID("context_id")
    val sourceType =
        pgEnum("source_type", "capability_source_type", CapabilitySourceType.entries)
}
