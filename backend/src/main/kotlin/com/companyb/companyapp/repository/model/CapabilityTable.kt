package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table

object CapabilityTable : Table("capability") {
    val id = uuid("id").autoGenerate()
    val code = text("code").uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}
