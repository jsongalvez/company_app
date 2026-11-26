package com.companyb.companyapp.database

import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every JDBC call that runs SQL reaches the driver timed and named, whichever overload the caller picks. */
class JdbcTimingTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @AfterTest
    fun end() = RequestQueryTally.endRequest()

    @Test
    fun `every overload that prepares SQL hands out a timed statement`() =
        assertEquals(emptyList(), untimed(prepares) { c, prepare -> prepare(c).use { it.execute() } })

    @Test
    fun `every overload that runs SQL text is timed`() =
        assertEquals(emptyList(), untimed(runs) { c, run -> c.createStatement().use { run(it) } })

    @Test
    fun `every way to run a prepared or callable statement is timed`() {
        val prepared = untimed(preparedRuns) { c, run -> c.prepareStatement(sqlFor(run)).use { run(it) } }
        val callable = untimed(preparedRuns) { c, run -> c.prepareCall(sqlFor(run)).use { run(it) } }
        assertEquals(emptyList(), prepared.map { "prepared $it" } + callable.map { "callable $it" })
    }

    @Test
    fun `the lists above name every JDBC overload that prepares or runs SQL`() {
        assertEquals(signatures(Connection::class.java) { name, _ -> name.startsWith("prepare") }, prepares.keys)
        assertEquals(signatures(Statement::class.java) { name, n -> name.startsWith("execute") && n > 0 }, runs.keys)
        for (type in listOf(PreparedStatement::class.java, CallableStatement::class.java)) {
            assertEquals(signatures(type) { name, n -> name.startsWith("execute") && n == 0 }, preparedRuns.keys)
        }
    }

    private fun <T> untimed(
        cases: Map<String, T>,
        run: (Connection, T) -> Unit,
    ): List<String> =
        JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource()).connection.use { connection ->
            cases.mapNotNull { (name, case) ->
                RequestQueryTally.startRequest()
                val failed = runCatching { run(connection, case) }.exceptionOrNull()
                val counted =
                    RequestQueryTally.snapshot().queries == 1 && RequestQueryTally.reads() == setOf("app_user")
                RequestQueryTally.endRequest()
                when {
                    failed != null -> "$name threw ${failed::class.simpleName}"
                    !counted -> name
                    else -> null
                }
            }
        }

    private fun signatures(
        type: Class<*>,
        keep: (String, Int) -> Boolean,
    ): Set<String> =
        type.methods
            .filter { keep(it.name, it.parameterCount) }
            .map { m -> "${m.name}(${m.parameterTypes.joinToString { it.simpleName }})" }
            .toSet()

    private fun sqlFor(run: (PreparedStatement) -> Any) = if (run === executeQuery) READ else WRITE

    private companion object {
        const val WRITE = "UPDATE app_user SET email = email WHERE false"
        const val READ = "SELECT id FROM app_user WHERE false"
        const val FORWARD = ResultSet.TYPE_FORWARD_ONLY
        const val READ_ONLY = ResultSet.CONCUR_READ_ONLY
        const val CLOSE = ResultSet.CLOSE_CURSORS_AT_COMMIT
        val noIndexes = IntArray(0)
        val noNames = emptyArray<String>()

        val prepares: Map<String, (Connection) -> PreparedStatement> =
            mapOf(
                "prepareStatement(String)" to { it.prepareStatement(WRITE) },
                "prepareStatement(String, int)" to { it.prepareStatement(WRITE, Statement.NO_GENERATED_KEYS) },
                "prepareStatement(String, int[])" to { it.prepareStatement(WRITE, noIndexes) },
                "prepareStatement(String, String[])" to { it.prepareStatement(WRITE, noNames) },
                "prepareStatement(String, int, int)" to { it.prepareStatement(WRITE, FORWARD, READ_ONLY) },
                "prepareStatement(String, int, int, int)" to { it.prepareStatement(WRITE, FORWARD, READ_ONLY, CLOSE) },
                "prepareCall(String)" to { it.prepareCall(WRITE) },
                "prepareCall(String, int, int)" to { it.prepareCall(WRITE, FORWARD, READ_ONLY) },
                "prepareCall(String, int, int, int)" to { it.prepareCall(WRITE, FORWARD, READ_ONLY, CLOSE) },
            )

        val runs: Map<String, (Statement) -> Any> =
            mapOf(
                "execute(String)" to { it.execute(WRITE) },
                "execute(String, int)" to { it.execute(WRITE, Statement.NO_GENERATED_KEYS) },
                "execute(String, int[])" to { it.execute(WRITE, noIndexes) },
                "execute(String, String[])" to { it.execute(WRITE, noNames) },
                "executeQuery(String)" to { it.executeQuery(READ) },
                "executeUpdate(String)" to { it.executeUpdate(WRITE) },
                "executeUpdate(String, int)" to { it.executeUpdate(WRITE, Statement.NO_GENERATED_KEYS) },
                "executeUpdate(String, int[])" to { it.executeUpdate(WRITE, noIndexes) },
                "executeUpdate(String, String[])" to { it.executeUpdate(WRITE, noNames) },
                "executeLargeUpdate(String)" to { it.executeLargeUpdate(WRITE) },
                "executeLargeUpdate(String, int)" to { it.executeLargeUpdate(WRITE, Statement.NO_GENERATED_KEYS) },
                "executeLargeUpdate(String, int[])" to { it.executeLargeUpdate(WRITE, noIndexes) },
                "executeLargeUpdate(String, String[])" to { it.executeLargeUpdate(WRITE, noNames) },
            )

        val executeQuery: (PreparedStatement) -> Any = { it.executeQuery() }

        val preparedRuns: Map<String, (PreparedStatement) -> Any> =
            mapOf(
                "execute()" to { it.execute() },
                "executeQuery()" to executeQuery,
                "executeUpdate()" to { it.executeUpdate() },
                "executeLargeUpdate()" to { it.executeLargeUpdate() },
                "executeBatch()" to { it.executeBatch() },
                "executeLargeBatch()" to { it.executeLargeBatch() },
            )
    }
}
