package com.companyb.companyapp.testsupport.database

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkerSchemaLifecycleTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @Test
    fun `worker schema leads search_path and current_schema matches`() {
        TestDatabaseLifecycle.ensureDatabase()
        val owned = TestDatabaseLifecycle.requireWorkerSchema()
        TestDatabaseLifecycle.requireTestDataSource().connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SHOW search_path")
                rs.next()
                val searchPath = rs.getString(1)
                assertTrue(searchPath.contains(owned), "search_path '$searchPath' misses '$owned'")
                assertTrue(searchPath.contains("public"), "search_path '$searchPath' misses 'public'")
                assertTrue(
                    searchPath.indexOf(owned) < searchPath.indexOf("public"),
                    "owned schema must precede public in '$searchPath'",
                )
            }
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT current_schema()")
                rs.next()
                assertEquals(owned, rs.getString(1))
            }
        }
    }

    @Test
    fun `extensions live in public and trigram helper works`() {
        TestDatabaseLifecycle.ensureDatabase()
        TestDatabaseLifecycle.requireTestDataSource().connection.use { conn ->
            assertExtensionsInPublic(conn)
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT similarity('hello', 'hallo') > 0.0")
                rs.next()
                assertTrue(rs.getBoolean(1), "pg_trgm similarity must work via worker search_path")
            }
        }
    }

    @Test
    fun `migrations views and snapshot trigger resolve in worker schema`() {
        TestDatabaseLifecycle.ensureDatabase()
        val owned = TestDatabaseLifecycle.requireWorkerSchema()
        TestDatabaseLifecycle.requireTestDataSource().connection.use { conn ->
            assertMigrationCount(conn)
            assertViewsPresent(conn, owned)
            assertSnapshotTrigger(conn, owned)
        }
        insertEnumProbe()
        transaction {
            exec("SELECT 1 FROM active_user_capabilities LIMIT 1")
            exec("SELECT 1 FROM active_session_voids LIMIT 1")
        }
    }

    @Test
    fun `second worker schema stays disjoint and disposal spares public sentinel`() {
        TestDatabaseLifecycle.ensureDatabase()
        val config = AppConfig.parse()
        val dbName = TestWorkerSchema.resolveDbName(config)
        val second = TestWorkerSchema.generate()
        TestWorkerSchema.requireOwned(second)
        val sentinel = sentinelTable()
        val url = TestWorkerSchema.jdbcUrl(config, dbName)
        val firstBranch = TestFixtures.uuid()
        createPublicSentinel(url, config, sentinel)
        try {
            val secondDs = openSecondWorker(config, dbName, second)
            try {
                val secondBranch = TestFixtures.uuid()
                insertFirstBranch(firstBranch)
                insertSecondBranch(secondDs, firstBranch, secondBranch)
                assertInitIsolation(secondBranch, sentinel)
            } finally {
                closeSecondWorker(secondDs, config, dbName, second)
            }
            assertPublicSentinelSurvived(url, config, sentinel)
            assertFirstRowSurvived(firstBranch)
            assertExtensionsSurvive()
        } finally {
            dropPublicSentinel(url, config, sentinel)
        }
    }

    @Test
    fun `guards reject app database and unowned schemas`() {
        TestDatabaseLifecycle.requireTestDatabase("company_app_test", "company_app")
        assertFailsWith<IllegalStateException> {
            TestDatabaseLifecycle.requireTestDatabase("company_app", "company_app")
        }
        assertFailsWith<IllegalStateException> {
            TestDatabaseLifecycle.requireTestDatabase("", "company_app")
        }
        assertTrue(TestDatabaseLifecycle.isOwnedSchema(TestDatabaseLifecycle.generateWorkerSchema()))
        assertTrue(!TestDatabaseLifecycle.isOwnedSchema("public"))
        assertTrue(!TestDatabaseLifecycle.isOwnedSchema(""))
        assertTrue(!TestDatabaseLifecycle.isOwnedSchema(null))
        assertTrue(!TestDatabaseLifecycle.isOwnedSchema("public_branch"))
        assertFailsWith<IllegalStateException> {
            TestDatabaseLifecycle.requireOwnedSchema("public")
        }
    }

    private fun assertExtensionsInPublic(conn: java.sql.Connection) {
        conn.createStatement().use { stmt ->
            val rs =
                stmt.executeQuery(
                    "SELECT extname, n.nspname FROM pg_extension e " +
                        "JOIN pg_namespace n ON n.oid = e.extnamespace " +
                        "WHERE extname IN ('btree_gist', 'pg_trgm', 'pg_stat_statements')",
                )
            val placements = mutableMapOf<String, String>()
            while (rs.next()) {
                placements[rs.getString(1)] = rs.getString(2)
            }
            for (ext in listOf("btree_gist", "pg_trgm", "pg_stat_statements")) {
                assertEquals("public", placements[ext], "extension '$ext' must stay in public")
            }
        }
    }

    private fun assertMigrationCount(conn: java.sql.Connection) {
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT version FROM flyway_schema_history WHERE version IS NOT NULL")
            val versions = mutableSetOf<String>()
            while (rs.next()) {
                versions.add(rs.getString(1))
            }
            assertTrue(versions.contains("1"), "worker schema must hold baseline V1, found $versions")
            assertTrue(versions.contains("2"), "worker schema must hold seed V2, found $versions")
            assertTrue(
                versions.none { it in setOf("3", "4", "5", "6") },
                "retired V3–V6 must stay folded into V1, found $versions",
            )
        }
    }

    private fun assertViewsPresent(
        conn: java.sql.Connection,
        owned: String,
    ) {
        conn.createStatement().use { stmt ->
            val rs =
                stmt.executeQuery(
                    "SELECT count(*) FROM information_schema.views " +
                        "WHERE table_schema = '$owned' " +
                        "AND table_name IN (" +
                        "'active_session_voids', 'active_user_capabilities', " +
                        "'daily_sales_summary', 'monthly_remittance_summary')",
                )
            rs.next()
            assertEquals(4, rs.getInt(1))
        }
    }

    private fun assertSnapshotTrigger(
        conn: java.sql.Connection,
        owned: String,
    ) {
        conn.createStatement().use { stmt ->
            val rs =
                stmt.executeQuery(
                    "SELECT count(*) FROM information_schema.triggers " +
                        "WHERE trigger_schema = '$owned' " +
                        "AND trigger_name = 'trg_remittance_snapshot_immutable'",
                )
            rs.next()
            assertTrue(rs.getInt(1) >= 1, "snapshot immutability trigger must exist in '$owned'")
        }
    }

    private fun insertEnumProbe() {
        val userId = TestFixtures.uuid()
        val branchId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(userId, "lifecycle")
        BranchWorkforceFixtures.insertTestBranch(branchId)
        val dayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val clientId = SessionClientFixtures.insertTestClient()
        val sessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(sessionId, clientId, dayId)
    }

    private fun sentinelTable(): String {
        val suffix =
            TestDatabaseLifecycle
                .requireWorkerSchema()
                .removePrefix("test_w_")
        return "worker_lifecycle_sentinel_$suffix"
    }

    private fun createPublicSentinel(
        url: String,
        config: AppConfig,
        sentinel: String,
    ) {
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE IF NOT EXISTS public.\"$sentinel\" (id UUID PRIMARY KEY)")
                stmt.execute("DELETE FROM public.\"$sentinel\"")
                stmt.execute("INSERT INTO public.\"$sentinel\" (id) VALUES ('${UUID(0L, 1L)}')")
            }
        }
    }

    private fun openSecondWorker(
        config: AppConfig,
        dbName: String,
        second: String,
    ): HikariDataSource {
        val ds = TestWorkerSchema.createDataSource(config, dbName, second)
        Flyway
            .configure()
            .dataSource(ds)
            .schemas(second)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        return ds
    }

    private fun insertFirstBranch(firstBranch: UUID) {
        BranchWorkforceFixtures.insertTestBranch(firstBranch)
    }

    private fun insertSecondBranch(
        ds: HikariDataSource,
        firstBranch: UUID,
        secondBranch: UUID,
    ) {
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM branch WHERE id = '$firstBranch'")
                rs.next()
                assertEquals(0, rs.getInt(1), "second schema must not see first worker rows")
                stmt.execute(
                    "INSERT INTO branch (id, name, branch_type) VALUES ('$secondBranch', 'S', 'CLINIC')",
                )
            }
        }
    }

    private fun assertInitIsolation(
        secondBranch: UUID,
        sentinel: String,
    ) {
        TestDatabaseLifecycle.requireTestDataSource().connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM public.\"$sentinel\"")
                rs.next()
                assertEquals(1, rs.getInt(1), "public sentinel must survive second worker init")
                val rs2 = stmt.executeQuery("SELECT count(*) FROM branch WHERE id = '$secondBranch'")
                rs2.next()
                assertEquals(0, rs2.getInt(1), "first worker must not see second schema rows")
            }
        }
    }

    private fun closeSecondWorker(
        ds: HikariDataSource,
        config: AppConfig,
        dbName: String,
        second: String,
    ) {
        runCatching { ds.close() }
        TestWorkerSchema.drop(config, dbName, second)
    }

    private fun assertPublicSentinelSurvived(
        url: String,
        config: AppConfig,
        sentinel: String,
    ) {
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM public.\"$sentinel\"")
                rs.next()
                assertEquals(1, rs.getInt(1), "public sentinel must survive second schema disposal")
            }
        }
    }

    private fun dropPublicSentinel(
        url: String,
        config: AppConfig,
        sentinel: String,
    ) {
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DROP TABLE IF EXISTS public.\"$sentinel\"")
            }
        }
    }

    private fun assertFirstRowSurvived(firstBranch: UUID) {
        TestDatabaseLifecycle.requireTestDataSource().connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM branch WHERE id = '$firstBranch'")
                rs.next()
                assertEquals(1, rs.getInt(1), "first worker row must survive second schema disposal")
            }
        }
    }

    private fun assertExtensionsSurvive() {
        TestDatabaseLifecycle.requireTestDataSource().connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT similarity('abc', 'abd') > 0.0")
                rs.next()
                assertTrue(rs.getBoolean(1), "extensions must survive worker-schema disposal")
            }
        }
    }
}
