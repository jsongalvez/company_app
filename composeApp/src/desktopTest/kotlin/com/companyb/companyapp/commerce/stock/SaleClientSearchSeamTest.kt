package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.client.ClientSearchApi
import com.companyb.companyapp.client.SaleClientSearchViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// #610 — seam fixture: commerce buyer selection receives only the narrow client-owned
// search surface, never the ClientViewModel edit/anonymize operations.
class SaleClientSearchSeamTest {
    @Test
    fun commerceStock_sources_reference_no_client_viewmodel() {
        val dir = locateStockDir()
        assertTrue(dir.isDirectory, "commerce/stock source dir missing (probed from ${java.io.File(".").absolutePath})")
        val hits =
            dir
                .listFiles { file -> file.extension == "kt" }
                .orEmpty()
                .filter { file ->
                    file.readText().contains("ClientViewModel")
                }.map { it.name }
        assertTrue(
            hits.isEmpty(),
            "commerce/stock must not reference ClientViewModel (narrow ClientSearchApi only): $hits",
        )
    }

    private fun locateStockDir(): java.io.File {
        val candidates =
            listOf(
                java.io.File("composeApp/src/commonMain/kotlin/com/companyb/companyapp/commerce/stock"),
                java.io.File("../composeApp/src/commonMain/kotlin/com/companyb/companyapp/commerce/stock"),
                java.io.File("../../composeApp/src/commonMain/kotlin/com/companyb/companyapp/commerce/stock"),
            )
        candidates.firstOrNull { it.isDirectory }?.let { return it }
        var current = java.io.File(".").absoluteFile
        repeat(6) {
            val marker = java.io.File(current, "settings.gradle.kts")
            val stock =
                java.io.File(
                    current,
                    "composeApp/src/commonMain/kotlin/com/companyb/companyapp/commerce/stock",
                )
            if (marker.exists() && stock.isDirectory) return stock
            current = current.parentFile ?: return@repeat
        }
        return candidates.first()
    }

    @Test
    fun clientSearchApi_exposes_only_search_surface() {
        val names =
            ClientSearchApi::class.java.methods
                .map { it.name }
                .toSet()
        assertTrue(names.contains("getQuery"), "ClientSearchApi must expose query: $names")
        assertTrue(names.contains("getSearchResults"), "ClientSearchApi must expose searchResults: $names")
        assertTrue(names.contains("getOnQueryChange"), "ClientSearchApi must expose onQueryChange: $names")
        assertTrue(names.contains("getRetrySearch"), "ClientSearchApi must expose retrySearch: $names")
        val forbidden = listOf("create", "update", "anonymize", "loadclient", "detail", "conflict", "notice")
        forbidden.forEach { token ->
            assertFalse(
                names.any { it.lowercase().contains(token) },
                "ClientSearchApi must not expose '$token' operations: $names",
            )
        }
    }

    @Test
    fun saleSearchViewModel_has_no_edit_operations() {
        val names =
            SaleClientSearchViewModel::class.java.methods
                .map { it.name }
                .toSet()
        val forbidden =
            listOf(
                "createClient",
                "updateClient",
                "anonymizeClient",
                "loadClient",
                "consumeCreateClientResult",
                "getClientDetail",
                "getAnonymizeState",
                "getCreateClientResult",
            )
        forbidden.forEach { method ->
            assertFalse(names.contains(method), "SaleClientSearchViewModel must not expose $method: $names")
        }
        assertTrue(names.contains("getQuery"), "sale search VM must expose query: $names")
        assertTrue(names.contains("getSearchResults"), "sale search VM must expose searchResults: $names")
        assertTrue(names.contains("applyClientMutation"), "sale search VM must reconcile mutations: $names")
    }

    @Test
    fun inventorySectionContext_holds_narrow_search_seam() {
        val paramTypes =
            InventorySectionContext::class.java.constructors
                .single()
                .parameterTypes
        assertTrue(
            paramTypes.contains(ClientSearchApi::class.java),
            "InventorySectionContext must hold ClientSearchApi: ${paramTypes.map { it.simpleName }}",
        )
        assertFalse(
            paramTypes.any { it.simpleName == "ClientViewModel" },
            "InventorySectionContext must not hold ClientViewModel: ${paramTypes.map { it.simpleName }}",
        )
    }
}
