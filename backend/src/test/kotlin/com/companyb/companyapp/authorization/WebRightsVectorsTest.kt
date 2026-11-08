package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.ActsResponse
import com.companyb.companyapp.contracts.authorization.RightsResponse
import com.companyb.companyapp.contracts.workforce.DelegateRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The web looks rights up in what [Rights] sends; it decides nothing itself. Each vector is one
 * person's [RightsResponse] and what [Access.allows] answers for every act at six places. This test
 * checks that the lookup the web does (everywhere, then the branch, then its day) gives those
 * answers, and `web/tests/unit/rights.test.ts` runs the web's own lookup over the same file.
 */
class WebRightsVectorsTest {
    @Serializable
    private data class Vector(
        val who: String,
        val rights: RightsResponse,
        val answers: Map<String, ActsResponse>,
    )

    @Serializable
    private data class Vectors(
        val acts: List<Act>,
        val places: Map<String, Map<String, String>>,
        val vectors: List<Vector>,
    )

    private val pretty = Json { prettyPrint = true }

    private val b1 = UUID(0, 1)
    private val b2 = UUID(0, 2)
    private val d1 = UUID(0, 3)
    private val d2 = UUID(0, 4)
    private val places =
        linkedMapOf(
            "everywhere" to Place.Everywhere,
            "b1" to Place.Branch(b1),
            "b2" to Place.Branch(b2),
            "b1/d1" to Place.Day(d1, b1),
            "b1/d2" to Place.Day(d2, b1),
            "b2/d1" to Place.Day(d1, b2),
        )

    private fun facts(
        roles: Set<Role> = emptySet(),
        homes: Set<UUID> = emptySet(),
        unassigned: Map<UUID, Set<Role>> = emptyMap(),
        relief: Set<UUID> = emptySet(),
        seats: Map<UUID, Set<DelegateRole>> = emptyMap(),
    ) = Facts(true, roles, homes, unassigned, relief, seats)

    private val people: Map<String, Facts> =
        Role.entries.associate { "${it.name.lowercase()} of b1" to facts(setOf(it), setOf(b1)) } +
            mapOf(
                "owner of no branch, reaching mission b2" to
                    facts(setOf(Role.OWNER), unassigned = mapOf(b2 to setOf(Role.OWNER))),
                "superuser of no branch" to facts(setOf(Role.SUPERUSER)),
                "practitioner of b2, relief on d1" to facts(setOf(Role.PRACTITIONER), setOf(b2), relief = setOf(d1)),
                "onboarding, relief on d1" to facts(setOf(Role.ONBOARDING), relief = setOf(d1)),
                "practitioner of no branch, coordinator helper at b2" to
                    facts(setOf(Role.PRACTITIONER), seats = mapOf(b2 to setOf(DelegateRole.COORDINATOR))),
                "accountant of b1, inactive" to
                    Facts(false, setOf(Role.ACCOUNTANT), setOf(b1), emptyMap(), emptySet(), emptyMap()),
            )

    private fun answer(
        grants: List<Grant>,
        place: Place,
    ): ActsResponse {
        val may = Act.entries.filter { Access.allows(grants, it, place) }
        val reads = Act.entries.filter { it in may || it.whenReading in may }
        return ActsResponse(may, reads)
    }

    private fun lookup(
        rights: RightsResponse,
        place: Place,
    ): ActsResponse {
        val levels =
            listOfNotNull(
                rights.everywhere,
                rights.branches.find { it.id == place.branchId?.toString() }?.let { ActsResponse(it.may, it.reads) },
                (place as? Place.Day)?.let { day -> rights.days.find { it.id == day.dayId.toString() } }?.let {
                    ActsResponse(it.may, it.reads)
                },
            )
        return ActsResponse(
            Act.entries.filter { act -> levels.any { act in it.may } },
            Act.entries.filter { act -> levels.any { act in it.reads } },
        )
    }

    private fun vectors(): Vectors {
        val label = mapOf(b1 to "b1", b2 to "b2", d1 to "d1", d2 to "d2")

        fun named(rights: RightsResponse) =
            rights.copy(
                branches = rights.branches.map { it.copy(id = label.getValue(UUID.fromString(it.id))) },
                days = rights.days.map { it.copy(id = label.getValue(UUID.fromString(it.id))) },
            )
        return Vectors(
            acts = Act.entries,
            places =
                places.mapValues { (_, p) ->
                    listOfNotNull(
                        p.branchId?.let { "branch" to label.getValue(it) },
                        (p as? Place.Day)?.let { "day" to label.getValue(it.dayId) },
                    ).toMap()
                },
            vectors =
                people.map { (who, facts) ->
                    val grants = Policy.grants(facts)
                    Vector(who, named(Rights.of(grants)), places.mapValues { (_, p) -> answer(grants, p) })
                },
        )
    }

    @Test
    fun `looking rights up gives what Access answers, for every person, act and place`() {
        val wrong =
            people.flatMap { (who, facts) ->
                val grants = Policy.grants(facts)
                val rights = Rights.of(grants)
                places.mapNotNull { (name, place) ->
                    val expected = answer(grants, place)
                    val got = lookup(rights, place)
                    if (got == expected) null else "$who at $name: expected $expected, looked up $got"
                }
            }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `the web's copy of the vectors is current`() {
        val file = File("web/tests/unit/rights.vectors.json")
        val expected = pretty.encodeToString(Vectors.serializer(), vectors()) + "\n"
        if (System.getenv("WRITE_WEB_RIGHTS") == "1") file.writeText(expected)
        assertEquals(
            expected,
            if (file.exists()) file.readText() else "",
            "${file.path} is stale: run WRITE_WEB_RIGHTS=1 ./gradlew :backend:test --tests '*WebRightsVectorsTest'",
        )
    }
}
