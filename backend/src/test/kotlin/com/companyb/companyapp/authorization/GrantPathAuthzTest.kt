package com.companyb.companyapp.authorization
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branch.BranchRoutes
import com.companyb.companyapp.contracts.authorization.ActsResponse
import com.companyb.companyapp.contracts.authorization.RightsResponse
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.MeRoutes
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.identity.RoleTable
import com.companyb.companyapp.identity.UserRoleTable
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
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
import kotlin.test.assertTrue

/**
 * #132 route-level proof: a role-derived GLOBAL grant now passes
 * Access.require (pre-#132 these routes 403'd for
 * every user — the dead-grant class). GET /api/branches gates GLOBAL
 * MANAGE_USERS; GET /api/me/capabilities reports the rights they give.
 */
class GrantPathAuthzTest : BasePostgresTest() {
    private val ownerUser = TestFixtures.uuid()
    private val accountantUser = TestFixtures.uuid()
    private val inactiveOwnerUser = TestFixtures.uuid()
    private val noRoleUser = TestFixtures.uuid()

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override fun initTestData() {
        listOf(
            ownerUser to "owner",
            accountantUser to "accountant",
            noRoleUser to "no-role",
        ).forEach { (id, prefix) ->
            IdentityFixtures.insertTestUser(id, prefix)
        }
        IdentityFixtures.insertUser(
            id = inactiveOwnerUser,
            username = "inactive-owner-${inactiveOwnerUser.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "inactive-owner-${inactiveOwnerUser.toString().take(8)}@t.st",
            displayName = "Test Inactive Owner",
            status = UserStatus.INACTIVE,
        )

        listOf(
            ownerUser to "OWNER",
            accountantUser to "ACCOUNTANT",
            inactiveOwnerUser to "OWNER",
        ).forEach { (userId, roleName) ->
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
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                MeRoutes.getCapabilities(cfg)
                BranchRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun getStatus(
        path: String,
        user: UUID,
    ): Int {
        var status = 0
        testServer.client.let { client ->
            status = client.get(path, asUser(user)).code
        }
        return status
    }

    private fun getRights(user: UUID): RightsResponse {
        var body: RightsResponse? = null
        testServer.client.let { client ->
            body = json.decodeFromString<RightsResponse>(client.get("/api/me/capabilities", asUser(user)).body.string())
        }
        return body!!
    }

    @Test
    fun `OWNER-role user passes the GLOBAL MANAGE_USERS gate`() {
        assertEquals(
            200,
            getStatus("/api/branches", ownerUser),
            "role-derived MANAGE_USERS must pass Access.require",
        )
    }

    @Test
    fun `ACCOUNTANT-role user is rejected by the MANAGE_USERS gate`() {
        assertEquals(403, getStatus("/api/branches", accountantUser), "ACCOUNTANT must not hold MANAGE_USERS")
    }

    @Test
    fun `INACTIVE OWNER-role user is rejected by the gate`() {
        assertEquals(403, getStatus("/api/branches", inactiveOwnerUser), "INACTIVE users must not derive grants")
    }

    @Test
    fun `me-capabilities reports what an owner's role gives everywhere`() {
        val everywhere = getRights(ownerUser).everywhere.may
        for (act in listOf(Act.MANAGE_USERS, Act.PAY, Act.ASSIGN_DELEGATE, Act.SEE_EVERY_BRANCH)) {
            assertTrue(act in everywhere, "owner /api/me/capabilities must allow $act everywhere")
        }
    }

    @Test
    fun `me-capabilities shows ACCOUNTANT all-branches read`() {
        assertTrue(
            Act.SEE_EVERY_BRANCH in getRights(accountantUser).everywhere.may,
            "ACCOUNTANT must see every branch (all-branches read, #131 window semantics)",
        )
    }

    @Test
    fun `zero-role user gets no rights`() {
        assertEquals(
            RightsResponse(ActsResponse(emptyList(), emptyList()), emptyList(), emptyList()),
            getRights(noRoleUser),
        )
    }
}
