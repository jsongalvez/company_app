package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime

data class UserCapability(
    val id: String,
    val userId: String,
    val capabilityId: String,
    val contextType: String,
    val contextId: String,
    val validFrom: OffsetDateTime,
    val validTo: OffsetDateTime?,
    val sourceType: String,
    val sourceId: String,
    val priority: Short,
)

enum class CapabilityContextType { GLOBAL, BRANCH, BRANCH_DAY, MEDICAL_MISSION, PROVINCIAL_TOUR }

enum class CapabilitySourceType { RELIEF_ACCESS, MEDICAL_MISSION_DELEGATE, MANUAL_OVERRIDE, SYSTEM }

object UserCapabilityTable : Table("user_capability") {
    val id = javaUUID("id").autoGenerate()
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val capabilityId = javaUUID("capability_id").references(CapabilityTable.id)
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
    val validFrom = timestampWithTimeZone("valid_from").defaultExpression(CurrentTimestampWithTimeZone)
    val validTo = timestampWithTimeZone("valid_to").nullable()
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
    val sourceId = javaUUID("source_id")
    val priority = short("priority").default(0)

    override val primaryKey = PrimaryKey(id)
}
