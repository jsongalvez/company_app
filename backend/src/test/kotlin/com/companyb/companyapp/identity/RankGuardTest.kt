package com.companyb.companyapp.identity

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
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
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pre-launch audit's rank and clock-in rules (owed #24, user message 13675).
 *
 * H1: only an Owner or a Superuser gives or takes away Owner or Manager; nobody changes their own
 * roles; only a Superuser changes or deactivates a Superuser; only an Owner or a Superuser
 * deactivates an Owner. M3: a clock-in needs a home at that branch or relief granted for the day.
 * M4: pay goes only to the branch's members and the day's relief, the payer included.
 */
class RankGuardTest : BasePostgresTest() {
    private val home = TestFixtures.uuid()
    private val elsewhere = TestFixtures.uuid()
    private val owner = TestFixtures.uuid()
    private val owner2 = TestFixtures.uuid()
    private val manager = TestFixtures.uuid()
    private val superuser = TestFixtures.uuid()
    private val superuser2 = TestFixtures.uuid()
    private val practitioner = TestFixtures.uuid()
    private val accountant = TestFixtures.uuid()
    private val nobody = TestFixtures.uuid()
    private val relief = TestFixtures.uuid()

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(home)
        BranchWorkforceFixtures.insertTestBranch(elsewhere)
        val roles =
            mapOf(
                owner to listOf("OWNER"),
                owner2 to listOf("OWNER"),
                manager to listOf("MANAGER"),
                superuser to listOf("SUPERUSER"),
                superuser2 to listOf("SUPERUSER"),
                practitioner to listOf("PRACTITIONER"),
                accountant to listOf("ACCOUNTANT"),
                nobody to emptyList(),
                relief to listOf("PRACTITIONER"),
            )
        roles.entries.forEachIndexed { i, (id, names) ->
            IdentityFixtures.insertTestUser(id, "rank$i")
            names.forEach { IdentityFixtures.assignRole(id, it) }
        }
        listOf(owner, owner2, manager, practitioner).forEachIndexed { i, id ->
            BranchWorkforceFixtures.insertTestAssignment(
                userId = id,
                branchId = home,
                slot = (i + 1).toShort(),
                assignedBy = id,
            )
        }
        val elsewhereToday = BranchWorkforceFixtures.createBranchDayForDate(elsewhere, TestFixtures.today)
        IdentityFixtures.grantCapability(
            relief,
            CapabilityCodes.EDIT_BRANCH_DATA,
            CapabilityContextType.BRANCH_DAY,
            elsewhereToday,
            TestFixtures.uuid(),
        )
    }

    @Test
    fun `a Manager cannot raise their own rank or remove the people above them`() {
        assertEquals(403, putRoles(manager, manager, "MANAGER", "OWNER"), "Manager makes self Owner")
        assertEquals(403, putRoles(manager, owner, "PRACTITIONER"), "Manager demotes the Owner")
        assertEquals(403, deactivate(manager, superuser), "Manager deactivates the Superuser")
        assertEquals(403, deactivate(manager, owner), "Manager deactivates the Owner")
        assertEquals(403, putRoles(manager, practitioner, "PRACTITIONER", "MANAGER"), "Manager makes a Manager")
        assertEquals(403, putRoles(manager, practitioner, "PRACTITIONER", "OWNER"), "Manager makes an Owner")
        assertEquals(403, invite(manager, "OWNER"), "Manager invites an Owner")
        assertEquals(403, putRoles(manager, superuser, "PRACTITIONER", "COORDINATOR"), "Manager changes a Superuser")
    }

    @Test
    fun `a Manager still manages everyone below them`() {
        assertEquals(204, putRoles(manager, practitioner, "PRACTITIONER", "COORDINATOR"))
        assertEquals(204, deactivate(manager, practitioner))
        assertEquals(204, putRoles(manager, superuser, "PRACTITIONER"), "the masked no-op still saves")
    }

    @Test
    fun `an Owner gives and takes Owner and Manager but never touches a Superuser or themselves`() {
        assertEquals(204, putRoles(owner, practitioner, "PRACTITIONER", "MANAGER"))
        assertEquals(204, putRoles(owner, manager, "PRACTITIONER"))
        assertEquals(204, deactivate(owner, owner2))
        assertEquals(403, putRoles(owner, owner, "PRACTITIONER"), "Owner changes own roles")
        assertEquals(403, deactivate(owner, superuser), "Owner deactivates the Superuser")
        assertEquals(403, putRoles(owner, superuser, "PRACTITIONER", "COORDINATOR"), "Owner changes a Superuser")
    }

    @Test
    fun `a Superuser manages another Superuser`() {
        assertEquals(204, deactivate(superuser, superuser2))
        assertEquals(204, putRoles(superuser, owner, "PRACTITIONER"))
    }

    @Test
    fun `clock-in needs a home at the branch or relief granted for the day`() {
        assertEquals(403, clockIn(accountant, home), "Accountant")
        assertEquals(403, clockIn(nobody, home), "no role")
        assertEquals(403, clockIn(practitioner, elsewhere), "member of another branch")
        assertEquals(403, clockIn(relief, home), "relief granted at another branch")
        assertEquals(201, clockIn(practitioner, home), "member")
        assertEquals(201, clockIn(relief, elsewhere), "relief on the granted day")
    }

    @Test
    fun `nobody pays themselves where they neither belong nor relieve`() {
        val away = BranchWorkforceFixtures.createBranchDayForDate(elsewhere, TestFixtures.today.minusDays(1))
        assertEquals(403, pay(owner, owner, away), "Owner pays self at a branch they are not a member of")
        val day = BranchWorkforceFixtures.createBranchDayForDate(home, TestFixtures.today.minusDays(1))
        assertEquals(201, pay(owner, owner, day), "Owner pays self at their own branch")
        assertEquals(201, pay(owner, practitioner, day), "Owner pays a member")
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun putRoles(
        caller: UUID,
        target: UUID,
        vararg roles: String,
    ): Int =
        testServer.client
            .put("${ApiRoutes.USERS}/$target/roles", mapOf("roles" to roles.toList()), asUser(caller))
            .code

    private fun deactivate(
        caller: UUID,
        target: UUID,
    ): Int = testServer.client.patch("${ApiRoutes.USERS}/$target/deactivate", null, asUser(caller)).code

    private fun invite(
        caller: UUID,
        vararg roles: String,
    ): Int = testServer.client.post(ApiRoutes.INVITES, mapOf("roles" to roles.toList()), asUser(caller)).code

    private fun clockIn(
        caller: UUID,
        branch: UUID,
    ): Int =
        testServer.client
            .post(
                ApiRoutes.ATTENDANCE_CLOCK_IN,
                mapOf("attendanceId" to TestFixtures.uuid().toString(), "branchId" to branch.toString()),
                asUser(caller),
            ).code

    private fun pay(
        caller: UUID,
        payee: UUID,
        day: UUID,
    ): Int =
        testServer.client
            .post(
                ApiRoutes.COMPENSATION,
                mapOf(
                    "id" to TestFixtures.uuid().toString(),
                    "workBranchDayId" to day.toString(),
                    "payingBranchDayId" to day.toString(),
                    "userId" to payee.toString(),
                    "amount" to "100.00",
                    "reason" to "rank check",
                ),
                asUser(caller),
            ).code

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
                    ctx.status(400).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, ctx ->
                    ctx.status(404).json(mapOf("error" to e.message))
                }
                cfg.routes.exception(ConflictException::class.java) { e, ctx ->
                    ctx.status(409).json(mapOf("error" to e.message))
                }
                registerAllRoutes(cfg)
            }
        }
    }
}
