package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.postgresql.util.PGobject

object ActiveUserCapabilitiesView : Table("active_user_capabilities") {
    val userId = javaUUID("user_id")
    val capabilityId = javaUUID("capability_id")
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
    val contextId = javaUUID("context_id")
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
