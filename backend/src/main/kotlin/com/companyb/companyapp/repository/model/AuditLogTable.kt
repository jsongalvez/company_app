package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject

object AuditLogTable : Table("audit_log") {
    val id = uuid("id").autoGenerate()
    val auditTableName = text("table_name")
    val recordId = uuid("record_id")
    val action =
        customEnumeration<AuditAction>(
            name = "action",
            sql = "audit_action",
            fromDb = { value -> AuditAction.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "audit_action"
                obj.value = it.name
                obj
            },
        )
    val changedBy = uuid("changed_by").references(AppUserTable.id)
    val changedAt = timestampWithTimeZone("changed_at").defaultExpression(CurrentTimestampWithTimeZone)
    val oldValue = registerColumn("old_value", JsonBColumnType()).nullable()
    val newValue = registerColumn("new_value", JsonBColumnType()).nullable()
    val isFlagged = bool("is_flagged").default(false)
    val reason = text("reason").nullable()
    val acknowledgedBy = uuid("acknowledged_by").references(AppUserTable.id).nullable()
    val acknowledgedAt = timestampWithTimeZone("acknowledged_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

class JsonBColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String =
        when (value) {
            is PGobject -> value.value ?: ""
            is String -> value
            else -> value.toString()
        }

    override fun valueToDB(value: String?): Any? {
        if (value == null) return null
        val obj = PGobject()
        obj.type = "jsonb"
        obj.value = value
        return obj
    }
}
