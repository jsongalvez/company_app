package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slow-query visibility (#474): after a slow-search repro, the normalized
 * statement (mean time, call count) is queryable via [SlowQueryRepository],
 * with bound literals masked to $n placeholders.
 */
class SlowQueryRepositoryPostgresTest : BasePostgresTest() {
    private val clientId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)
    }

    @Test
    fun `topSlow is selectable and every row has at least one call`() {
        val top = SlowQueryRepository.topSlow()

        assertTrue(top.all { it.calls >= MIN_CALL_COUNT })
    }

    @Test
    fun `client search repro appears normalized with masked literals`() {
        val token = "ZqxjWv474"
        ClientRepository.search(token)

        val clientEntries =
            SlowQueryRepository
                .topSlow(SLOW_QUERY_SCAN_LIMIT)
                .filter { it.query.contains(CLIENT_TABLE_TOKEN, ignoreCase = true) }
        assumeTrue(
            "pg_stat_statements tracking off — needs shared_preload_libraries=pg_stat_statements plus a server restart",
            clientEntries.isNotEmpty(),
        )

        assertTrue(clientEntries.all { it.calls >= MIN_CALL_COUNT })
        assertFalse(
            clientEntries.any { it.query.contains(token) },
            "normalized query text must mask bound literals",
        )
    }

    companion object {
        private const val MIN_CALL_COUNT = 1L
        private const val SLOW_QUERY_SCAN_LIMIT = 50
        private const val CLIENT_TABLE_TOKEN = "client"
    }
}
