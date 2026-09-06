package com.companyb.companyapp.repository.model
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject

object AuditLogTable : Table("audit_log") {
    val id = javaUUID("id").autoGenerate()
    val auditTableName = text("table_name")
    val recordId = javaUUID("record_id")
    val action =
        customEnumeration<AuditAction>(
            name = "action",
            sql = "audit_action",
            // SAFETY: PG enum column binds as String via customEnumeration #467
            fromDb = { value -> AuditAction.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "audit_action"
                obj.value = it.name
                obj
            },
        )
    val changedBy = javaUUID("changed_by").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id).nullable()
    val changedAt = timestampWithTimeZone("changed_at").defaultExpression(CurrentTimestampWithTimeZone)
    val oldValue = registerColumn("old_value", JsonBColumnType()).nullable()
    val newValue = registerColumn("new_value", JsonBColumnType()).nullable()
    val isFlagged = bool("is_flagged").default(false)
    val reason = text("reason").nullable()
    val acknowledgedBy = javaUUID("acknowledged_by").references(AppUserTable.id).nullable()
    val acknowledgedAt = timestampWithTimeZone("acknowledged_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

class JsonBColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String =
        when (value) {
            is PGobject -> value.value.orEmpty()
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
