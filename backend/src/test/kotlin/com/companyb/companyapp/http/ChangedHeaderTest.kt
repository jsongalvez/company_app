package com.companyb.companyapp.http

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.configureHttpSurface
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.RequestLog
import com.companyb.companyapp.registerExceptionHandlers
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import io.javalin.Javalin
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Response
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The app's own filters tell a signed-in caller which tables its request changed, on every way out. */
class ChangedHeaderTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private val config =
        AppConfig(
            appPort = 0,
            dbHost = "",
            dbPort = "",
            dbName = "",
            dbUser = "",
            dbPassword = "",
            jwtSecret = "x".repeat(64),
            jwtIssuer = "i",
            jwtAudience = "a",
            authDummyPassword = "d",
        )

    private fun app(): Javalin {
        val db = Database.connect(JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource()))
        val write = { transaction(db) { exec("UPDATE app_user SET email = email WHERE false") } }
        return Javalin.create { cfg ->
            configureHttpSurface(cfg, config)
            registerExceptionHandlers(cfg)
            cfg.routes.before("/api/*") { it.attribute("userId", "signed-in") }
            cfg.routes.post("/api/write") {
                write()
                it.result("ok")
            }
            cfg.routes.post("/api/refused") {
                write()
                throw ValidationException("refused")
            }
            cfg.routes.get("/api/read") {
                transaction(db) { exec("SELECT 1") }
                it.result("ok")
            }
            cfg.routes.post("/out/write") {
                write()
                it.result("ok")
            }
        }
    }

    private fun Response.changed(): String? = headers().get(RequestLog.CHANGED_HEADER)?.firstOrNull()

    @Test
    fun `a write names the table it changed`() =
        JavalinTest.test(app()) { _, client ->
            assertEquals("app_user", client.post("/api/write").changed())
        }

    @Test
    fun `a refused write still names the table it reached`() =
        JavalinTest.test(app()) { _, client ->
            val response = client.post("/api/refused")
            assertEquals(400, response.code)
            assertEquals("app_user", response.changed())
        }

    @Test
    fun `a read says it changed nothing`() =
        JavalinTest.test(app()) { _, client ->
            assertEquals("", client.get("/api/read").changed())
        }

    @Test
    fun `a signed-out caller is told nothing`() =
        JavalinTest.test(app()) { _, client ->
            assertNull(client.post("/out/write").changed())
        }
}
