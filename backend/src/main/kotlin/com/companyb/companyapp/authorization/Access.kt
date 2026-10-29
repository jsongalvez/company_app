package com.companyb.companyapp.authorization

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import io.javalin.http.Context
import java.util.UUID

/**
 * Which of a person's grants count for an action. A grant sits at a home branch (BRANCH), at
 * every branch (GLOBAL), or at one day (BRANCH_DAY, which is how relief is granted).
 */
enum class Reach {
    /** A GLOBAL grant only. */
    EVERYWHERE,

    /** A grant of the code anywhere at all. */
    ANYWHERE,

    /** A BRANCH grant at the place's branch. */
    HOME,

    /** A BRANCH grant at the place's branch, or a GLOBAL grant. */
    HOME_OR_EVERYWHERE,

    /** A BRANCH grant at the place's branch, or a BRANCH_DAY grant for the place's day. GLOBAL does not count. */
    HOME_OR_DAY,

    /** [HOME_OR_EVERYWHERE], or relief at the place's day (a BRANCH_DAY EDIT_BRANCH_DATA grant). */
    HOME_OR_EVERYWHERE_OR_RELIEF_DAY,
}

/**
 * Every action the server gates, with the capability it needs and the grants that count. This is
 * the one place to read who may do what; `AccessTableTest` pins the outcome per role and day.
 * Day state (closed and remitted days) is checked on top by `BranchDayService`, using
 * [CHANGE_CLOSED_DAY].
 */
enum class Act(
    val code: String,
    val reach: Reach,
) {
    SEE_BRANCH(CapabilityCodes.VIEW_BRANCH_DATA, Reach.HOME_OR_EVERYWHERE),
    SEE_HOME_BRANCH(CapabilityCodes.VIEW_BRANCH_DATA, Reach.HOME),
    SEE_EVERY_BRANCH(CapabilityCodes.VIEW_BRANCH_DATA, Reach.EVERYWHERE),
    SEE_DAY(CapabilityCodes.VIEW_BRANCH_DATA, Reach.HOME_OR_DAY),
    SEE_DAY_SUMMARY(CapabilityCodes.VIEW_BRANCH_DATA, Reach.HOME_OR_EVERYWHERE_OR_RELIEF_DAY),
    WORK_DAY(CapabilityCodes.EDIT_BRANCH_DATA, Reach.HOME_OR_DAY),
    WORK_HOME_BRANCH(CapabilityCodes.EDIT_BRANCH_DATA, Reach.HOME),
    WORK_ANYWHERE(CapabilityCodes.EDIT_BRANCH_DATA, Reach.ANYWHERE),
    VOID(CapabilityCodes.VOID_SESSION, Reach.HOME),
    CHANGE_CLOSED_DAY(CapabilityCodes.EDIT_PAST_DAY, Reach.HOME),
    PAY(CapabilityCodes.ASSIGN_COMPENSATION, Reach.HOME),
    SUBMIT_REMITTANCE(CapabilityCodes.SUBMIT_REMITTANCE, Reach.HOME),
    MANAGE_STOCK(CapabilityCodes.MANAGE_PRODUCTS, Reach.HOME),
    MANAGE_CATALOG(CapabilityCodes.MANAGE_CATALOG, Reach.EVERYWHERE),
    MANAGE_USERS(CapabilityCodes.MANAGE_USERS, Reach.EVERYWHERE),
    ASSIGN_DELEGATE(CapabilityCodes.ASSIGN_DELEGATE, Reach.EVERYWHERE),
}

/** Where an action happens. */
sealed interface Place {
    val branchId: UUID?

    data object Everywhere : Place {
        override val branchId: UUID? = null
    }

    data class Branch(
        override val branchId: UUID,
    ) : Place

    data class Day(
        val dayId: UUID,
        override val branchId: UUID,
    ) : Place

    companion object {
        /** The day [dayId] at its branch. Throws NotFoundException when the day does not exist. */
        fun day(dayId: UUID): Day = Day(dayId, BranchDayService.requireBranchDayExists(dayId).branchId)

        /** Today at [branchId] once the day exists, else the branch itself (a grant always names an existing day). */
        fun today(branchId: UUID): Place =
            BranchDayService.findToday(branchId)?.let { Day(it.id, branchId) } ?: Branch(branchId)
    }
}

/** The only way the server asks whether someone may do something. */
object Access {
    fun allows(
        userId: UUID,
        act: Act,
        place: Place = Place.Everywhere,
    ): Boolean {
        if (act.reach == Reach.ANYWHERE) return CapabilityRepository.hasCapabilityAnyContext(userId, act.code)
        val home = place.branchId?.let { CapabilityContextType.BRANCH to it }
        val everywhere = CapabilityContextType.GLOBAL to CapabilityService.GLOBAL_CONTEXT_ID
        val day = (place as? Place.Day)?.let { CapabilityContextType.BRANCH_DAY to it.dayId }
        val contexts =
            when (act.reach) {
                Reach.EVERYWHERE -> listOf(everywhere)
                Reach.HOME -> listOfNotNull(home)
                Reach.HOME_OR_EVERYWHERE, Reach.HOME_OR_EVERYWHERE_OR_RELIEF_DAY -> listOfNotNull(home, everywhere)
                Reach.HOME_OR_DAY -> listOfNotNull(home, day)
                Reach.ANYWHERE -> error("handled above")
            }
        if (CapabilityRepository.holdsIn(userId, act.code, contexts)) return true
        return act.reach == Reach.HOME_OR_EVERYWHERE_OR_RELIEF_DAY &&
            day != null &&
            CapabilityRepository.holdsIn(userId, CapabilityCodes.EDIT_BRANCH_DATA, listOf(day))
    }

    fun require(
        userId: UUID,
        act: Act,
        place: Place = Place.Everywhere,
        message: String = defaultMessage(act),
    ) {
        if (!allows(userId, act, place)) throw ForbiddenException(message)
    }

    fun require(
        context: Context,
        act: Act,
        place: Place = Place.Everywhere,
        message: String = defaultMessage(act),
    ) = require(context.callerUuid(), act, place, message)

    private fun defaultMessage(act: Act): String =
        when (act.reach) {
            Reach.EVERYWHERE, Reach.ANYWHERE -> {
                "${act.code} capability required"
            }

            Reach.HOME, Reach.HOME_OR_EVERYWHERE -> {
                "${act.code} capability required for this branch"
            }

            Reach.HOME_OR_DAY, Reach.HOME_OR_EVERYWHERE_OR_RELIEF_DAY -> {
                "${act.code} capability required for this branch or day"
            }
        }
}
