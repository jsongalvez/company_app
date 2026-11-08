package com.companyb.companyapp.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

class GrantWriterArchitectureTest {
    @Test
    fun `only GrantStore writes relief days`() {
        val files =
            BackendArchitectureOwners.discover().mapValues { (path, source) ->
                BackendArchitectureOwners.parseKt(source, path)
            }
        val tables = BackendArchitectureOwners.treeTableNames(files)
        val writers =
            files
                .filter { (_, file) ->
                    BackendArchitectureOwners
                        .tableWriteOps(file, tables)
                        .any { it.substringBefore('.') == "ReliefDayTable" }
                }.keys
                .map { it.substringAfterLast("companyapp/") }
        assertEquals(
            listOf("authorization/GrantStore.kt"),
            writers,
            "relief days change only through GrantStore (reach it via AuthorizationGrants)",
        )
    }
}
