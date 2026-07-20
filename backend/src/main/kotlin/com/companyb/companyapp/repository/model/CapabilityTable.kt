package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object CapabilityTable : Table("capability") {
    val id = javaUUID("id").autoGenerate()
    val code = text("code").uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}
