package com.companyb.companyapp.api.routes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
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
 * CapabilityFilter.requireGlobalCapability (pre-#132 these routes 403'd for
 * every user — the dead-grant class). GET /api/branches gates GLOBAL
 * MANAGE_USERS; GET /api/me/capabilities reports the derived rows.
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
            DatabaseTestHelper.insertTestUser(id, prefix)
            trackOwned(AppUserTable, AppUserTable.id, id)
        }
        DatabaseTestHelper.insertUser(
            id = inactiveOwnerUser,
            username = "inactive-owner-${inactiveOwnerUser.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "inactive-owner-${inactiveOwnerUser.toString().take(8)}@t.st",
            displayName = "Test Inactive Owner",
            status = UserStatus.INACTIVE,
        )
        trackOwned(AppUserTable, AppUserTable.id, inactiveOwnerUser)

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
            trackOwned(UserRoleTable, UserRoleTable.userId, userId)
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
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
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

    private fun getCapabilities(user: UUID): List<UserCapabilityResponse> {
        var body: List<UserCapabilityResponse> = emptyList()
        testServer.client.let { client ->
            val response = client.get("/api/me/capabilities", asUser(user))
            body =
                response.body
                    .string()
                    .takeIf { it.isNotBlank() }
                    ?.let { json.decodeFromString<List<UserCapabilityResponse>>(it) }
                    ?: emptyList()
        }
        return body
    }

    @Test
    fun `OWNER-role user passes the GLOBAL MANAGE_USERS gate`() {
        assertEquals(
            200,
            getStatus("/api/branches", ownerUser),
            "role-derived MANAGE_USERS must pass requireGlobalCapability",
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
    fun `me-capabilities reports role-derived rows`() {
        val ownerCaps = getCapabilities(ownerUser)
        val codes = ownerCaps.map { it.capabilityCode }.toSet()
        assertTrue(
            codes.contains(CapabilityCodes.MANAGE_USERS),
            "owner /api/me/capabilities must include role-derived MANAGE_USERS",
        )
        assertTrue(
            codes.contains(CapabilityCodes.ASSIGN_COMPENSATION),
            "owner /api/me/capabilities must include role-derived ASSIGN_COMPENSATION",
        )
        assertTrue(
            codes.contains(CapabilityCodes.ASSIGN_DELEGATE),
            "owner /api/me/capabilities must include role-derived ASSIGN_DELEGATE",
        )
        assertTrue(
            ownerCaps.filter { it.capabilityCode == CapabilityCodes.MANAGE_USERS }.all { it.sourceType.name == "ROLE" },
            "derived rows must carry sourceType ROLE",
        )
        assertTrue(
            ownerCaps.any {
                it.capabilityCode == CapabilityCodes.VIEW_BRANCH_DATA &&
                    it.contextType.name == "GLOBAL" &&
                    it.contextId == "00000000-0000-0000-0000-000000000000"
            },
            "OWNER must see role-derived GLOBAL VIEW_BRANCH_DATA for all-branches read",
        )
    }

    @Test
    fun `me-capabilities shows ACCOUNTANT all-branches read`() {
        val accountantCaps = getCapabilities(accountantUser)
        assertTrue(
            accountantCaps.any {
                it.capabilityCode == CapabilityCodes.VIEW_BRANCH_DATA &&
                    it.contextType.name == "GLOBAL" &&
                    it.contextId == "00000000-0000-0000-0000-000000000000"
            },
            "ACCOUNTANT must see GLOBAL VIEW_BRANCH_DATA (all-branches read, #131 window semantics)",
        )
    }

    @Test
    fun `zero-role user gets an empty capabilities list`() {
        assertTrue(getCapabilities(noRoleUser).isEmpty())
    }
}
