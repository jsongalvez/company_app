package com.companyb.companyapp.identity

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.registerAllRoutes
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * Role rules the user set (message 13065, owed #23 and #24):
 * an Accountant "can't be a member of any branch", and "superusers should appear as practitioners
 * to someone managing users", which must not cost the superuser the role when such a person saves.
 */
class RoleRulesTest : BasePostgresTest() {
    private val branchId = TestFixtures.uuid()
    private val owner = TestFixtures.uuid()
    private val superuser = TestFixtures.uuid()
    private val member = TestFixtures.uuid()
    private val accountant = TestFixtures.uuid()
    private val loner = TestFixtures.uuid()

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(branchId)
        mapOf(
            owner to "OWNER",
            superuser to "SUPERUSER",
            member to "PRACTITIONER",
            accountant to "ACCOUNTANT",
            loner to "PRACTITIONER",
        ).forEach { (id, role) ->
            IdentityFixtures.insertTestUser(id, "u${id.toString().take(8)}")
            assignRole(id, role)
        }
        BranchWorkforceFixtures.insertTestAssignment(userId = member, branchId = branchId, slot = 1, assignedBy = owner)
    }

    @Test
    fun `a branch member can't be made an Accountant`() {
        val r =
            testServer.client.put(
                "${ApiRoutes.USERS}/$member/roles",
                mapOf("roles" to listOf("ACCOUNTANT")),
                asUser(owner),
            )
        assertEquals(400, r.code, r.body.string())
        assertEquals(listOf("PRACTITIONER"), rolesOf(member))
    }

    @Test
    fun `someone with no home branch can be made an Accountant`() {
        val r =
            testServer.client.put(
                "${ApiRoutes.USERS}/$loner/roles",
                mapOf("roles" to listOf("ACCOUNTANT")),
                asUser(owner),
            )
        assertEquals(204, r.code, r.body.string())
        assertEquals(listOf("ACCOUNTANT"), rolesOf(loner))
    }

    @Test
    fun `an Accountant can't be given a home branch`() {
        val body = mapOf("id" to TestFixtures.uuid().toString(), "userId" to accountant.toString(), "slot" to 2)
        val r = testServer.client.post("${ApiRoutes.BRANCHES}/$branchId/assignments", body, asUser(owner))
        assertEquals(400, r.code, r.body.string())
    }

    @Test
    fun `an Owner sees a Superuser as a Practitioner, a Superuser sees the truth`() {
        assertEquals(listOf("PRACTITIONER"), listedRoles(superuser, asUser(owner)))
        assertEquals(listOf("SUPERUSER"), listedRoles(superuser, asUser(superuser)))
    }

    @Test
    fun `an Owner saving a Superuser's roles as shown keeps the Superuser role`() {
        val shown = listedRoles(superuser, asUser(owner))
        val r = testServer.client.put("${ApiRoutes.USERS}/$superuser/roles", mapOf("roles" to shown), asUser(owner))
        assertEquals(204, r.code, r.body.string())
        assertEquals(listOf("SUPERUSER"), rolesOf(superuser))
    }

    @Test
    fun `an Owner adding a role to a Superuser adds it and keeps Superuser`() {
        val r =
            testServer.client.put(
                "${ApiRoutes.USERS}/$superuser/roles",
                mapOf("roles" to listOf("PRACTITIONER", "MANAGER")),
                asUser(owner),
            )
        assertEquals(204, r.code, r.body.string())
        assertEquals(listOf("MANAGER", "SUPERUSER"), rolesOf(superuser))
    }

    private fun listedRoles(
        who: UUID,
        caller: Consumer<Request.Builder>,
    ): List<String> {
        val r = testServer.client.get(ApiRoutes.USERS, caller)
        val body = r.body.string()
        assertEquals(200, r.code, body)
        val row =
            Json.parseToJsonElement(body).jsonArray.map { it.jsonObject }.single {
                it["id"]?.jsonPrimitive?.content ==
                    who.toString()
            }
        return row.getValue("roles").jsonArray.map { it.jsonPrimitive.content }
    }

    private fun rolesOf(id: UUID): List<String> =
        transaction {
            RoleRepository.findRoleNamesForUserInTransaction(id)
        }.sorted()

    private fun asUser(id: UUID) = Consumer<Request.Builder> { it.header("X-Test-User", id.toString()) }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        val roleId = transaction { RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id] }
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] = roleId
            }
        }
    }

    companion object {
        private val defaultUser = TestFixtures.uuid()

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
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(
                        mapOf("error" to e.message),
                    )
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(
                        mapOf("error" to e.message),
                    )
                }
                registerAllRoutes(cfg)
            }
        }
    }
}
