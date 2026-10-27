package com.companyb.companyapp.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

/**
 * A Postgres enum column read by name from a prebuilt map. Exposed's `customEnumeration`
 * checks every cell with Kotlin reflection (`isSubclassOf`), which showed as 11% of the CPU
 * of a Today read on a 1,000-session day.
 */
private class PgEnumColumnType<T : Enum<T>>(
    private val sql: String,
    values: Collection<T>,
) : ColumnType<T>() {
    private val byName: Map<String, T> = values.associateBy { it.name }

    override fun sqlType(): String = sql

    override fun valueFromDB(value: Any): T =
        byName[value.toString()] ?: throw IllegalArgumentException("No $sql value named $value")

    override fun notNullValueToDB(value: T): Any =
        PGobject().also {
            it.type = sql
            it.value = value.name
        }

    override fun nonNullValueToString(value: T): String = "'${value.name}'"
}

fun <T : Enum<T>> Table.pgEnum(
    name: String,
    sql: String,
    values: Collection<T>,
): Column<T> = registerColumn(name, PgEnumColumnType(sql, values))
