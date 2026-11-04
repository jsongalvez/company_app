package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.database.pgEnum
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject

internal object UserCapabilityTable : Table("user_capability") {
    val id = javaUUID("id").autoGenerate()
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val capabilityId = javaUUID("capability_id").references(CapabilityTable.id)
    val contextType =
        pgEnum("context_type", "capability_context_type", CapabilityContextType.entries)
    val contextId = javaUUID("context_id")
    val validFrom = timestampWithTimeZone("valid_from").defaultExpression(CurrentTimestampWithTimeZone)
    val validTo = timestampWithTimeZone("valid_to").nullable()
    val sourceType =
        pgEnum("source_type", "capability_source_type", CapabilitySourceType.entries)
    val sourceId = javaUUID("source_id")
    val priority = short("priority").default(0)

    override val primaryKey = PrimaryKey(id)
}
