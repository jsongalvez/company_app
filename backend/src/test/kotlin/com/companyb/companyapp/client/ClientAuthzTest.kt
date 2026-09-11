package com.companyb.companyapp.client

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #896: client routes gate EDIT_BRANCH_DATA in any context (the #660 concern-catalog
 * precedent) — clients are global records with no branch context, and EDIT_BRANCH_DATA
 * never derives GLOBAL, so a GLOBAL-only gate 403'd every production holder.
 * Role-derived BRANCH holders (OWNER/MANAGER/COORDINATOR/PRACTITIONER via ACTIVE
 * assignments) and windowed BRANCH_DAY relief holders pass; grantless callers 403.
 * No fixture-only direct GLOBAL EDIT grants — every holder here is production-mintable.
 */
class ClientAuthzTest : BasePostgresTest() {
    private val ownerUser = TestFixtures.uuid()
    private val managerUser = TestFixtures.uuid()
    private val coordinatorUser = TestFixtures.uuid()
    private val practitionerUser = TestFixtures.uuid()
    private val reliefUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val anonymizeClientId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(ownerUser, "client-owner")
        IdentityFixtures.insertTestUser(managerUser, "client-manager")
        IdentityFixtures.insertTestUser(coordinatorUser, "client-coordinator")
        IdentityFixtures.insertTestUser(practitionerUser, "client-practitioner")
        IdentityFixtures.insertTestUser(reliefUser, "client-relief")
        IdentityFixtures.insertTestUser(noneUser, "client-none")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Client Authz Branch $branchId")
        assignRole(ownerUser, "OWNER")
        assignRole(managerUser, "MANAGER")
        assignRole(coordinatorUser, "COORDINATOR")
        assignRole(practitionerUser, "PRACTITIONER")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = ownerUser,
            branchId = branchId,
            slot = 1,
            assignedBy = ownerUser,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = managerUser,
            branchId = branchId,
            slot = 2,
            assignedBy = ownerUser,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = coordinatorUser,
            branchId = branchId,
            slot = 3,
            assignedBy = ownerUser,
        )
        BranchWorkforceFixtures.insertTestAssignment(
            userId = practitionerUser,
            branchId = branchId,
            slot = 4,
            assignedBy = ownerUser,
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.grantCapability(
            userId = reliefUser,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH_DAY,
            contextId = branchDayId,
            sourceId = sourceId,
        )
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestClient(anonymizeClientId)
    }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId =
            transaction {
                RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    companion object {
        private val DEFAULT_USER = TestFixtures.uuid()

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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                ClientRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `GET client detail allowed for role-derived BRANCH holders`() {
        assertEquals(200, testServer.client.get("/api/clients/$clientId", asUser(ownerUser)).code)
        assertEquals(200, testServer.client.get("/api/clients/$clientId", asUser(managerUser)).code)
        assertEquals(200, testServer.client.get("/api/clients/$clientId", asUser(coordinatorUser)).code)
        assertEquals(200, testServer.client.get("/api/clients/$clientId", asUser(practitionerUser)).code)
    }

    @Test
    fun `GET client detail allowed for BRANCH_DAY relief holder`() {
        assertEquals(200, testServer.client.get("/api/clients/$clientId", asUser(reliefUser)).code)
    }

    @Test
    fun `GET client detail forbidden without grant`() {
        assertEquals(403, testServer.client.get("/api/clients/$clientId", asUser(noneUser)).code)
    }

    @Test
    fun `PATCH client allowed for role-derived BRANCH holder`() {
        assertEquals(
            200,
            testServer.client
                .patch(
                    "/api/clients/$clientId",
                    mapOf("firstName" to "Renamed"),
                    asUser(coordinatorUser),
                ).code,
        )
    }

    @Test
    fun `PATCH client allowed for BRANCH_DAY relief holder`() {
        assertEquals(
            200,
            testServer.client
                .patch(
                    "/api/clients/$clientId",
                    mapOf("firstName" to "ReliefRename"),
                    asUser(reliefUser),
                ).code,
        )
    }

    @Test
    fun `PATCH client forbidden without grant`() {
        assertEquals(
            403,
            testServer.client.patch("/api/clients/$clientId", mapOf("firstName" to "Hacked"), asUser(noneUser)).code,
        )
    }

    @Test
    fun `POST anonymize allowed for role-derived BRANCH holder`() {
        assertEquals(
            204,
            testServer.client
                .post(
                    "/api/clients/$anonymizeClientId/anonymize",
                    emptyMap<String, String>(),
                    asUser(ownerUser),
                ).code,
        )
    }

    @Test
    fun `POST anonymize forbidden without grant`() {
        assertEquals(
            403,
            testServer.client
                .post(
                    "/api/clients/$anonymizeClientId/anonymize",
                    emptyMap<String, String>(),
                    asUser(noneUser),
                ).code,
        )
    }

    @Test
    fun `POST client create allowed for role-derived BRANCH holder`() {
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "firstName" to "Authz",
                "lastName" to "Created",
                "gender" to "M",
                "age" to 30,
            )
        assertEquals(201, testServer.client.post("/api/clients", body, asUser(coordinatorUser)).code)
    }

    @Test
    fun `POST client create forbidden without grant`() {
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "firstName" to "Authz",
                "lastName" to "Blocked",
                "gender" to "M",
                "age" to 30,
            )
        assertEquals(403, testServer.client.post("/api/clients", body, asUser(noneUser)).code)
    }

    @Test
    fun `GET clients collection allowed for role-derived BRANCH holder`() {
        assertEquals(200, testServer.client.get("/api/clients?q=Test", asUser(coordinatorUser)).code)
    }

    @Test
    fun `GET clients collection allowed for BRANCH_DAY relief holder`() {
        assertEquals(200, testServer.client.get("/api/clients?q=Test", asUser(reliefUser)).code)
    }

    @Test
    fun `GET clients collection forbidden without grant`() {
        assertEquals(403, testServer.client.get("/api/clients?q=Test", asUser(noneUser)).code)
    }
}
