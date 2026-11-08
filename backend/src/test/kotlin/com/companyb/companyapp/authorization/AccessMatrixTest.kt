package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.identity.RoleRepository
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [Access.allows] answers for every act, person and place, and the grants each person's
 * `/api/me/capabilities` lists, and each role's codes as `/api/roles` lists them, pinned in [FILE]. Rewrite it with
 * `WRITE_ACCESS_MATRIX=1 ./gradlew :backend:test --tests '*AccessMatrixTest'` and read the diff:
 * a changed cell is a changed rule.
 */
class AccessMatrixTest : BasePostgresTest() {
    private val clinic = TestFixtures.uuid()
    private val otherClinic = TestFixtures.uuid()
    private val mission = TestFixtures.uuid()
    private val people = Who.entries.associateWith { TestFixtures.uuid() }
    private lateinit var today: UUID
    private lateinit var earlier: UUID
    private lateinit var labels: Map<UUID, String>

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(clinic)
        BranchWorkforceFixtures.insertTestBranch(otherClinic)
        BranchWorkforceFixtures.insertTestBranch(mission, branchType = BranchType.MEDICAL_MISSION)
        today = BranchWorkforceFixtures.createBranchDayForDate(clinic, TestFixtures.today)
        earlier = BranchWorkforceFixtures.createBranchDayForDate(clinic, TestFixtures.today.minusDays(1))
        labels =
            mapOf(
                clinic to "clinic",
                otherClinic to "other-clinic",
                mission to "mission",
                today to "today",
                earlier to "earlier",
                CapabilityService.GLOBAL_CONTEXT_ID to "everywhere",
            )
        Who.entries.forEachIndexed { slot, who ->
            val id = people.getValue(who)
            IdentityFixtures.insertTestUser(id, who.name.lowercase())
            who.roles.forEach { IdentityFixtures.assignRole(id, it) }
            who.home(this)?.let {
                BranchWorkforceFixtures.insertTestAssignment(
                    userId = id,
                    branchId = it,
                    slot = (slot + 1).toShort(),
                    assignedBy = id,
                )
            }
        }
        transaction {
            AppUserTable.update({ AppUserTable.id eq people.getValue(Who.INACTIVE_MANAGER) }) {
                it[AppUserTable.status] = UserStatus.INACTIVE
            }
            relief(Who.RELIEF)
            relief(Who.RELIEF_ENDED)
            UserCapabilityTable.update({ UserCapabilityTable.userId eq people.getValue(Who.RELIEF_ENDED) }) {
                it[UserCapabilityTable.validFrom] = OffsetDateTime.now().minusDays(2)
                it[UserCapabilityTable.validTo] = OffsetDateTime.now().minusDays(1)
            }
            helper(Who.HELPER, DelegateRole.PRACTITIONER)
            helper(Who.HELPER_COORDINATOR, DelegateRole.COORDINATOR)
            val removed = helper(Who.HELPER_REMOVED, DelegateRole.PRACTITIONER)
            AuthorizationGrants.closeDelegateCapabilityInTransaction(removed)
        }
    }

    private fun relief(who: Who) =
        AuthorizationGrants.grantReliefCapabilityInTransaction(
            GrantReliefCapabilityParams(people.getValue(who), today, TestFixtures.uuid(), validTo = null),
        )

    private fun helper(
        who: Who,
        role: DelegateRole,
    ): UUID =
        TestFixtures.uuid().also {
            AuthorizationGrants.grantDelegateCapabilityInTransaction(people.getValue(who), mission, it, role)
        }

    private fun places(): List<Pair<String, Place>> =
        listOf(
            "everywhere" to Place.Everywhere,
            "clinic" to Place.Branch(clinic),
            "today" to Place.Day(today, clinic),
            "earlier" to Place.Day(earlier, clinic),
            "other-clinic" to Place.Branch(otherClinic),
            "mission" to Place.Branch(mission),
        )

    private fun render(): String =
        buildString {
            val places = places()
            appendLine("# Access.allows: Y allowed, N refused. Columns: ${places.joinToString(" ") { it.first }}")
            Who.entries.forEach { who ->
                appendLine()
                appendLine("## ${who.name} (${who.roles.joinToString("+")})")
                Act.entries.forEach { act ->
                    val cells =
                        places.joinToString(" ") { (_, place) ->
                            if (Access.allows(people.getValue(who), act, place)) "Y" else "N"
                        }
                    appendLine("${act.name.padEnd(ACT_WIDTH)} $cells")
                }
                appendLine("grants:")
                CapabilityService
                    .getCapabilitiesForUser(people.getValue(who))
                    .map { g ->
                        val at = labels[UUID.fromString(g.contextId)] ?: "unknown"
                        "  ${g.capabilityCode} ${g.contextType} $at ${g.sourceType}"
                    }.sorted()
                    .forEach { appendLine(it) }
            }
            appendLine()
            appendLine("## /api/roles")
            RoleRepository.findAllWithCapabilities().forEach {
                appendLine(
                    "${it.name}: ${it.capabilities.joinToString(" ")}",
                )
            }
        }

    @Test
    fun `Access answers what the file says for every act, person and place`() {
        val observed = render()
        val file = File(FILE)
        if (System.getenv("WRITE_ACCESS_MATRIX") == "1") file.writeText(observed)
        assertEquals(
            file.readText(),
            observed,
            "$FILE is stale: run WRITE_ACCESS_MATRIX=1 ./gradlew :backend:test --tests '*AccessMatrixTest' " +
                "and read the diff",
        )
    }

    enum class Who(
        val roles: List<String>,
        val home: (AccessMatrixTest) -> UUID?,
    ) {
        SUPERUSER(listOf("SUPERUSER"), { it.clinic }),
        SUPERUSER_AWAY(listOf("SUPERUSER"), { null }),
        OWNER(listOf("OWNER"), { it.clinic }),
        OWNER_AWAY(listOf("OWNER"), { null }),
        MANAGER(listOf("MANAGER"), { it.clinic }),
        MANAGER_AWAY(listOf("MANAGER"), { null }),
        COORDINATOR(listOf("COORDINATOR"), { it.clinic }),
        PRACTITIONER(listOf("PRACTITIONER"), { it.clinic }),
        NEWCOMER(listOf("PRACTITIONER"), { null }),
        OUTSIDER(listOf("PRACTITIONER"), { it.otherClinic }),
        ACCOUNTANT(listOf("ACCOUNTANT"), { null }),
        ONBOARDING(listOf("ONBOARDING"), { it.clinic }),
        RELIEF(listOf("PRACTITIONER"), { null }),
        RELIEF_ENDED(listOf("PRACTITIONER"), { null }),
        HELPER(listOf("PRACTITIONER"), { null }),
        HELPER_COORDINATOR(listOf("PRACTITIONER"), { null }),
        HELPER_REMOVED(listOf("PRACTITIONER"), { null }),
        INACTIVE_MANAGER(listOf("MANAGER"), { it.clinic }),
    }

    private companion object {
        const val FILE = "backend/src/test/resources/access-matrix.txt"
        const val ACT_WIDTH = 18
    }
}
