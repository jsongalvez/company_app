package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

object ActiveUserCapabilitiesView : Table("active_user_capabilities") {
    val userId = uuid("user_id")
    val capabilityId = uuid("capability_id")
    val contextType =
        customEnumeration<CapabilityContextType>(
            name = "context_type",
            sql = "capability_context_type",
            fromDb = { value -> CapabilityContextType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "capability_context_type"
                obj.value = it.name
                obj
            },
        )
    val contextId = uuid("context_id")
    val priority = short("priority")
    val sourceType =
        customEnumeration<CapabilitySourceType>(
            name = "source_type",
            sql = "capability_source_type",
            fromDb = { value -> CapabilitySourceType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "capability_source_type"
                obj.value = it.name
                obj
            },
        )
}
