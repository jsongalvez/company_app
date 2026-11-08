package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.ActsResponse
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.PlaceActsResponse
import com.companyb.companyapp.contracts.authorization.RightsResponse
import java.util.UUID

/**
 * [Access.allows] for every act and every place a person stands at, for the web to look up.
 * Rights only grow from everywhere to a branch to one of its days, so each level lists what it adds.
 */
object Rights {
    fun of(grants: List<Grant>): RightsResponse {
        val everywhere = allowed(grants, Place.Everywhere)
        val branches =
            grants
                .filter { it.contextType == CapabilityContextType.BRANCH }
                .map { it.contextId }
                .distinct()
                .sorted()
        val withoutBranches = grants.filter { it.contextType != CapabilityContextType.BRANCH }
        val days =
            grants
                .filter { it.contextType == CapabilityContextType.BRANCH_DAY }
                .map { it.contextId }
                .distinct()
                .sorted()
        return RightsResponse(
            everywhere = acts(everywhere),
            branches = branches.map { place(it, allowed(grants, Place.Branch(it)) - everywhere) },
            days = days.map { place(it, allowed(withoutBranches, Place.Day(it, NO_BRANCH)) - everywhere) },
        )
    }

    /** Stands in for a day's branch where no branch grant is left to match it. */
    private val NO_BRANCH: UUID = CapabilityService.GLOBAL_CONTEXT_ID

    private fun allowed(
        grants: List<Grant>,
        place: Place,
    ): Set<Act> = Act.entries.filter { Access.allows(grants, it, place) }.toSet()

    private fun reading(may: Set<Act>): List<Act> = Act.entries.filter { it in may || it.whenReading in may }

    private fun acts(may: Set<Act>) = ActsResponse(may = Act.entries.filter { it in may }, reads = reading(may))

    private fun place(
        id: UUID,
        may: Set<Act>,
    ) = PlaceActsResponse(id = id.toString(), may = Act.entries.filter { it in may }, reads = reading(may))
}
