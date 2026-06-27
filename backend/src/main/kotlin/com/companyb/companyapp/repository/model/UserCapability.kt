package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
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
    private const val ENUM_LENGTH = 50

    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(AppUserTable.id)
    val capabilityId = uuid("capability_id").references(CapabilityTable.id)
    val contextType = enumerationByName<CapabilityContextType>("context_type", ENUM_LENGTH)
    val contextId = uuid("context_id")
    val validFrom = timestampWithTimeZone("valid_from").defaultExpression(CurrentTimestampWithTimeZone)
    val validTo = timestampWithTimeZone("valid_to").nullable()
    val sourceType = enumerationByName<CapabilitySourceType>("source_type", ENUM_LENGTH)
    val sourceId = uuid("source_id")
    val priority = short("priority").default(0)

    override val primaryKey = PrimaryKey(id)
}
