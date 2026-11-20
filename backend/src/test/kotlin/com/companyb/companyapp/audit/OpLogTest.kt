package com.companyb.companyapp.audit

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.configureAuth
import com.companyb.companyapp.configureHttpSurface
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.registerExceptionHandlers
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.HttpClient
import io.javalin.testtools.JavalinTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A save sent twice from a phone's queue is done once: the resend gets the first answer. */
class OpLogTest : BasePostgresTest() {
    private val alice = TestFixtures.uuid()
    private val bob = TestFixtures.uuid()
    private val runs = AtomicInteger()
    private val started = java.util.concurrent.CountDownLatch(1)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(alice, "oplog-alice")
        IdentityFixtures.insertTestUser(bob, "oplog-bob")
    }

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
        JwtService.init(config)
        return Javalin.create { cfg ->
            configureHttpSurface(cfg, config)
            configureAuth(cfg)
            registerExceptionHandlers(cfg)
            cfg.routes.post("/api/count") { it.json(mapOf("n" to runs.incrementAndGet())) }
            cfg.routes.post("/api/slow") {
                runs.incrementAndGet()
                started.countDown()
                Thread.sleep(1500)
                it.json(mapOf("n" to runs.get()))
            }
            cfg.routes.post("/api/refuse") {
                runs.incrementAndGet()
                throw ValidationException("no")
            }
        }
    }

    private fun HttpClient.send(
        path: String,
        user: java.util.UUID,
        op: String?,
    ) = post(path, "{}") { req ->
        req.header("Authorization", "Bearer ${JwtService.generateToken(user.toString())}")
        if (op != null) req.header(OpLog.HEADER, op)
    }

    @Test
    fun `a resend of a taken save answers as before and runs nothing`() =
        JavalinTest.test(app()) { _, client ->
            val first = client.send("/api/count", alice, "op-1")
            val again = client.send("/api/count", alice, "op-1")
            assertEquals(1, runs.get())
            assertEquals(first.code, again.code)
            assertEquals(first.body.string(), again.body.string())
            assertEquals("1", again.headers().get(OpLog.REPLAYED_HEADER)?.firstOrNull())
        }

    @Test
    fun `a resend while the first copy still runs waits instead of running beside it`() =
        JavalinTest.test(app()) { _, client ->
            val first =
                java.util.concurrent.CompletableFuture.supplyAsync {
                    client.send(
                        "/api/slow",
                        alice,
                        "op-hung",
                    )
                }
            started.await(10, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(503, client.send("/api/slow", alice, "op-hung").code)
            assertEquals(200, first.get().code)
            assertEquals(200, client.send("/api/slow", alice, "op-hung").code)
            assertEquals(1, runs.get())
        }

    @Test
    fun `a refused save runs again when sent again`() =
        JavalinTest.test(app()) { _, client ->
            assertEquals(400, client.send("/api/refuse", alice, "op-2").code)
            assertEquals(400, client.send("/api/refuse", alice, "op-2").code)
            assertEquals(2, runs.get())
        }

    @Test
    fun `one person's op id is not another's`() =
        JavalinTest.test(app()) { _, client ->
            client.send("/api/count", alice, "op-3")
            val other = client.send("/api/count", bob, "op-3")
            assertEquals(2, runs.get())
            assertNull(other.headers().get(OpLog.REPLAYED_HEADER)?.firstOrNull())
        }

    @Test
    fun `a save without an op id runs every time`() =
        JavalinTest.test(app()) { _, client ->
            client.send("/api/count", alice, null)
            client.send("/api/count", alice, null)
            assertEquals(2, runs.get())
        }
}
