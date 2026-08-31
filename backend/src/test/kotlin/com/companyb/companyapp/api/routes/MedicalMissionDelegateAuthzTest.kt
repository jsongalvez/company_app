package com.companyb.companyapp.api.routes

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.service.MedicalMissionDelegateService
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

class MedicalMissionDelegateAuthzTest : BasePostgresTest() {
    private val managerUser = TestFixtures.uuid()
    private val noGrantUser = TestFixtures.uuid()
    private val ownerUser = TestFixtures.uuid()
    private val targetUser = TestFixtures.uuid()
    private val missionBranch = TestFixtures.uuid()
    private val clinicBranch = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private val json = Json { ignoreUnknownKeys = true }

    override fun initTestData() {
        listOf(managerUser, noGrantUser, ownerUser, targetUser).forEach { userId ->
            DatabaseTestHelper.insertTestUser(userId, "delegate-${userId.toString().take(6)}")
            trackOwned(AppUserTable, AppUserTable.id, userId)
        }
        assignRole(targetUser, "MANAGER")
        assignRole(ownerUser, "OWNER")
        DatabaseTestHelper.grantAssignDelegate(managerUser, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, managerUser)
        insertBranch(missionBranch, BranchType.MEDICAL_MISSION)
        insertBranch(clinicBranch, BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, missionBranch)
        trackOwned(BranchTable, BranchTable.id, clinicBranch)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, managerUser)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, ownerUser)
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
                cfg.routes.before { context ->
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
                    context.attribute("userId", context.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, context ->
                    context.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                cfg.routes.exception(NotFoundException::class.java) { e, context ->
                    context.status(404).json(mapOf("error" to (e.message ?: "Not Found")))
                }
                cfg.routes.exception(ValidationException::class.java) { e, context ->
                    context.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                cfg.routes.exception(ConflictException::class.java) { e, context ->
                    context.status(409).json(mapOf("error" to (e.message ?: "Conflict")))
                }
                MedicalMissionDelegateRoutes.listDelegates(cfg)
                MedicalMissionDelegateRoutes.assignDelegate(cfg)
                MedicalMissionDelegateRoutes.revokeDelegate(cfg)
            }
        }
    }

    private fun asUser(userId: UUID): Consumer<Request.Builder> =
        Consumer { it.header("X-Test-User", userId.toString()) }

    @Test
    fun `GET branch delegates is forbidden without global ASSIGN_DELEGATE`() {
        testServer.client.let { client ->
            assertEquals(403, client.get("/api/branches/$missionBranch/delegates", asUser(noGrantUser)).code)
        }
    }

    @Test
    fun `GET branch delegates returns active and revoked rows`() {
        val revokedId = TestFixtures.uuid()
        val activeId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(revokedId, targetUser, missionBranch, managerUser)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, revokedId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUser)
        MedicalMissionDelegateService.revokeDelegate(revokedId, managerUser)
        MedicalMissionDelegateService.assignDelegate(activeId, targetUser, missionBranch, managerUser)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, activeId)

        testServer.client.let { client ->
            val response = client.get("/api/branches/$missionBranch/delegates", asUser(managerUser))
            assertEquals(200, response.code)
            val rows = json.decodeFromString<List<DelegateResponse>>(response.body.string())
            assertEquals(listOf(activeId.toString(), revokedId.toString()), rows.map { it.id })
            assertTrue(rows.first().endedAt == null)
            assertTrue(rows.last().endedAt != null)
        }
    }

    @Test
    fun `GET non-medical branch delegates returns bad request`() {
        testServer.client.let { client ->
            assertEquals(400, client.get("/api/branches/$clinicBranch/delegates", asUser(managerUser)).code)
        }
    }

    @Test
    fun `POST delegate rejects target without MANAGER role`() {
        val body =
            mapOf(
                "delegateId" to TestFixtures.uuid().toString(),
                "targetUserId" to noGrantUser.toString(),
                "branchId" to missionBranch.toString(),
            )
        testServer.client.let { client ->
            val response = client.post("/api/delegates", body, asUser(managerUser))
            assertEquals(400, response.code)
            assertTrue(response.body.string().contains("active MANAGER"))
        }
    }

    @Test
    fun `POST delegate is forbidden without global ASSIGN_DELEGATE`() {
        val body =
            mapOf(
                "delegateId" to TestFixtures.uuid().toString(),
                "targetUserId" to targetUser.toString(),
                "branchId" to missionBranch.toString(),
            )
        testServer.client.let { client ->
            assertEquals(403, client.post("/api/delegates", body, asUser(noGrantUser)).code)
        }
    }

    @Test
    fun `POST delegate accepts role-derived global ASSIGN_DELEGATE`() {
        val delegateId = TestFixtures.uuid()
        val body =
            mapOf(
                "delegateId" to delegateId.toString(),
                "targetUserId" to targetUser.toString(),
                "branchId" to missionBranch.toString(),
            )
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUser)

        testServer.client.let { client ->
            val response = client.post("/api/delegates", body, asUser(ownerUser))
            assertEquals(201, response.code)
        }
    }

    @Test
    fun `DELETE delegate is forbidden without global ASSIGN_DELEGATE`() {
        val delegateId = TestFixtures.uuid()
        MedicalMissionDelegateService.assignDelegate(delegateId, targetUser, missionBranch, managerUser)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUser)
        testServer.client.let { client ->
            assertEquals(403, client.delete("/api/delegates/$delegateId", null, asUser(noGrantUser)).code)
        }
    }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        trackOwned(UserRoleTable, UserRoleTable.userId, userId)
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] =
                    RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            }
        }
    }

    private fun insertBranch(
        branchId: UUID,
        branchType: BranchType,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = branchId
                it[BranchTable.name] = "Delegate ${branchId.toString().take(8)}"
                it[BranchTable.branchType] = branchType
            }
        }
    }
}
