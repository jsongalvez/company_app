package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.workforce.DelegateRole
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.AccessFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What [Access.allows] answers, for every [Act], for each kind of person, at each kind of place, as one
 * file: [FILE]. A rule change, or a move of the rules from one place to another, shows as a diff of that
 * file. Rewrite it with `WRITE_ACCESS_MATRIX=1 ./gradlew :backend:test --tests '*AccessMatrixTest'` and
 * read the diff.
 *
 * Places, in column order: [Place.Everywhere]; the clinic; today at the clinic; another day at the clinic;
 * another clinic; a medical mission. Y allows, N refuses. Persons are built from facts only: roles, a
 * branch assignment, a relief day, a helper place, an inactive account.
 */
class AccessMatrixTest : BasePostgresTest() {
    private val clinic = TestFixtures.uuid()
    private val otherClinic = TestFixtures.uuid()
    private val mission = TestFixtures.uuid()
    private lateinit var places: List<Place>

    private class Person(
        val name: String,
        val id: UUID = TestFixtures.uuid(),
    )

    private val persons = mutableListOf<Person>()

    override fun initTestData() {
        BranchWorkforceFixtures.insertTestBranch(clinic)
        BranchWorkforceFixtures.insertTestBranch(otherClinic)
        BranchWorkforceFixtures.insertTestBranch(mission, branchType = BranchType.MEDICAL_MISSION)
        val today = BranchWorkforceFixtures.createBranchDayForDate(clinic, TestFixtures.today)
        val another = BranchWorkforceFixtures.createBranchDayForDate(clinic, TestFixtures.today.minusDays(1))
        places =
            listOf(
                Place.Everywhere,
                Place.Branch(clinic),
                Place.Day(today, clinic),
                Place.Day(another, clinic),
                Place.Branch(otherClinic),
                Place.Branch(mission),
            )
        var slot = 0
        Role.entries.forEach { role ->
            person("${role.name} member") { id ->
                AccessFixtures.hold(id, role)
                assign(id, clinic, ++slot)
            }
            person("${role.name} away") { id -> AccessFixtures.hold(id, role) }
        }
        person("PRACTITIONER member of other clinic") { id ->
            AccessFixtures.hold(id, Role.PRACTITIONER)
            assign(id, otherClinic, ++slot)
        }
        person("PRACTITIONER member of mission") { id ->
            AccessFixtures.hold(id, Role.PRACTITIONER)
            assign(id, mission, ++slot)
        }
        person("PRACTITIONER relief today") { id -> AccessFixtures.relief(id, today) }
        person("PRACTITIONER helper at mission") { id ->
            AccessFixtures.helper(id, mission, DelegateRole.PRACTITIONER)
        }
        person("PRACTITIONER helper at mission as COORDINATOR") { id ->
            AccessFixtures.helper(id, mission, DelegateRole.COORDINATOR)
        }
        person("PRACTITIONER helper removed") { id ->
            AccessFixtures.endHelper(AccessFixtures.helper(id, mission))
        }
        person("MANAGER member, inactive", UserStatus.INACTIVE) { id ->
            AccessFixtures.hold(id, Role.MANAGER)
            assign(id, clinic, ++slot)
        }
        person("no roles member") { id -> assign(id, clinic, ++slot) }
    }

    private fun person(
        name: String,
        status: UserStatus = UserStatus.ACTIVE,
        facts: (UUID) -> Unit,
    ) {
        val person = Person(name)
        IdentityFixtures.insertUser(
            id = person.id,
            username = "m-${person.id.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "${person.id.toString().take(8)}@t.st",
            displayName = name,
            status = status,
        )
        facts(person.id)
        persons += person
    }

    private fun assign(
        userId: UUID,
        branchId: UUID,
        slot: Int,
    ) {
        BranchWorkforceFixtures.insertTestAssignment(
            userId = userId,
            branchId = branchId,
            slot = slot.toShort(),
            assignedBy = userId,
        )
    }

    @Test
    fun `Access gives each person the answers in the file`() {
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

    private fun render(): String =
        buildString {
            appendLine("# Access.allows per person and act. Columns: everywhere, clinic, today,")
            appendLine("# another day, other clinic, mission.")
            persons.forEach { person ->
                appendLine()
                appendLine("## ${person.name}")
                Act.entries.forEach { act ->
                    val answers = places.joinToString("") { if (Access.allows(person.id, act, it)) "Y" else "N" }
                    appendLine("${act.name.padEnd(NAME_WIDTH)}$answers")
                }
            }
        }

    private companion object {
        const val FILE = "backend/src/test/resources/access-matrix.txt"
        const val NAME_WIDTH = 20
    }
}
