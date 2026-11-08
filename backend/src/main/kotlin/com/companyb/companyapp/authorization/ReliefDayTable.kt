package com.companyb.companyapp.authorization

import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** A person working one branch day as relief, granted by [sourceId] (a relief request or invite). */
internal object ReliefDayTable : Table("relief_day") {
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val branchDayId = javaUUID("branch_day_id")
    val sourceId = javaUUID("source_id")
    val validTo = timestampWithTimeZone("valid_to").nullable()

    override val primaryKey = PrimaryKey(userId, branchDayId, sourceId)
}
