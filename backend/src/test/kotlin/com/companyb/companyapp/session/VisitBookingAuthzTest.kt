package com.companyb.companyapp.session

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Viewing a branch's booked visits (read with the Today screen) is not the right to book. */
class VisitBookingAuthzTest : BasePostgresTest() {
    private val viewer = TestFixtures.uuid()
    private val outsider = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(viewer, "booking-viewer")
        IdentityFixtures.insertTestUser(outsider, "booking-outsider")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Booking Branch $branchId")
        IdentityFixtures.grantCapability(
            userId = viewer,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = TestFixtures.uuid(),
        )
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(TestDatabaseLifecycle.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: TestFixtures.uuid().toString())
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                VisitBookingRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `viewing is not booking`() {
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "clientId" to TestFixtures.uuid().toString(),
                "visitDate" to "2030-01-01",
            )
        assertEquals(403, testServer.client.post("/api/branches/$branchId/bookings", body, asUser(viewer)).code)
    }

    @Test
    fun `someone with no right at the branch cannot book`() {
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "clientId" to TestFixtures.uuid().toString(),
                "visitDate" to "2030-01-01",
            )
        assertEquals(403, testServer.client.post("/api/branches/$branchId/bookings", body, asUser(outsider)).code)
    }
}
