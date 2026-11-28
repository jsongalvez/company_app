package com.companyb.companyapp.api

import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.http.NotFoundResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `GatedHandler` itself asks `Access` for the act a `Gate.Can` or `Gate.At` declares, at the place `Gate.At`
 * finds, so the act `RouteGateTest` reads is the act that refuses. A member of one branch calls each route.
 */
class GatedHandlerTest : BasePostgresTest() {
    override fun initTestData() {
        IdentityFixtures.insertTestUser(member, "gated-member")
        IdentityFixtures.insertTestUser(newUser, "gated-onboarding")
        BranchWorkforceFixtures.insertTestBranch(home, "Gated Home $home")
        BranchWorkforceFixtures.insertTestBranch(other, "Gated Other $other")
        IdentityFixtures.member(member, home)
        ran.set(0)
    }

    @Test
    fun `At passes for the declared act at the place it finds`() {
        assertEquals(200, post("/at/work-home/$home"))
        assertEquals(1, ran.get())
    }

    @Test
    fun `At refuses the declared act at another place, before the handler`() {
        assertEquals(403, post("/at/work-home/$other"))
        assertEquals(0, ran.get())
    }

    @Test
    fun `At refuses an act the caller lacks at a place where another act passes`() {
        assertEquals(403, post("/at/manage-stock/$home"))
        assertEquals(0, ran.get())
    }

    @Test
    fun `At answers the finder's 404 before any access check`() {
        assertEquals(404, post("/at/missing/$home"))
        assertEquals(0, ran.get())
    }

    @Test
    fun `Can checks the declared act everywhere`() {
        assertEquals(200, post("/can/work-anywhere"))
        assertEquals(403, post("/can/manage-users"))
        assertEquals(1, ran.get())
    }

    @Test
    fun `Staff allows active staff and refuses onboarding accounts`() {
        assertEquals(200, testServer.client.post("/staff/only", "{}", asUser(member)).code)
        assertEquals(403, testServer.client.post("/staff/only", "{}", asUser(newUser)).code)
        assertEquals(1, ran.get())
    }

    @Test
    fun `Pick checks each act it picks, in order, at its place`() {
        assertEquals(200, post("/pick/work-home/$home"))
        assertEquals(403, post("/pick/work-home/$other"))
        assertEquals(403, post("/pick/work-then-stock/$home"))
        assertEquals(1, ran.get())
    }

    @Test
    fun `Pick fails a picked act the route does not declare, before the handler`() {
        assertEquals(500, post("/pick/undeclared/$home"))
        assertEquals(0, ran.get())
    }

    private fun post(path: String): Int = testServer.client.post(path, "{}", asUser(member)).code

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    companion object {
        private val member = TestFixtures.uuid()
        private val newUser = TestFixtures.uuid()
        private val home = TestFixtures.uuid()
        private val other = TestFixtures.uuid()
        private val ran = AtomicInteger()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin =
            Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: TestFixtures.uuid().toString())
                }
                cfg.routes.exception(ForbiddenException::class.java) { _, ctx -> ctx.status(403) }
                val branch: (io.javalin.http.Context) -> Place = { Place.Branch(UUID.fromString(it.pathParam("id"))) }
                cfg.post("/at/work-home/{id}", Gate.At(Act.WORK_HOME_BRANCH, where = branch)) { ran.incrementAndGet() }
                cfg.post("/at/manage-stock/{id}", Gate.At(Act.MANAGE_STOCK, where = branch)) { ran.incrementAndGet() }
                cfg.post("/at/missing/{id}", Gate.At(Act.WORK_HOME_BRANCH) { throw NotFoundResponse() }) {
                    ran.incrementAndGet()
                }
                val home: (io.javalin.http.Context) -> Gate.Need = { Gate.Need(Act.WORK_HOME_BRANCH, branch(it)) }
                val work = setOf(Act.WORK_HOME_BRANCH, Act.MANAGE_STOCK)
                cfg.post("/pick/work-home/{id}", Gate.Pick(work) { listOf(home(it)) }) { ran.incrementAndGet() }
                cfg.post(
                    "/pick/work-then-stock/{id}",
                    Gate.Pick(work) { listOf(home(it), Gate.Need(Act.MANAGE_STOCK, branch(it))) },
                ) {
                    ran.incrementAndGet()
                }
                cfg.post("/pick/undeclared/{id}", Gate.Pick(setOf(Act.MANAGE_STOCK)) { listOf(home(it)) }) {
                    ran.incrementAndGet()
                }
                cfg.post("/can/work-anywhere", Gate.Can(Act.WORK_ANYWHERE)) { ran.incrementAndGet() }
                cfg.post("/can/manage-users", Gate.Can(Act.MANAGE_USERS)) { ran.incrementAndGet() }
                cfg.post("/staff/only", Gate.Staff("test")) { ran.incrementAndGet() }
            }
    }
}
