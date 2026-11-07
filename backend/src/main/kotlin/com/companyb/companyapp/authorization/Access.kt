package com.companyb.companyapp.authorization

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import io.javalin.http.Context
import io.javalin.http.HandlerType
import java.time.LocalDate
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

    /** A BRANCH grant at the place's branch, a GLOBAL grant, or a BRANCH_DAY grant for the place's day. */
    HOME_OR_EVERYWHERE_OR_DAY,
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

        /** The day at [branchId] on [date] once it exists, else the branch itself. */
        fun onDate(
            branchId: UUID,
            date: LocalDate,
        ): Place =
            BranchDayService.findByBranchAndDate(branchId, date)?.let { Day(it.id, branchId) } ?: Branch(branchId)
    }
}

/** The only way the server asks whether someone may do something. */
object Access {
    /**
     * The grant rows that count for [reach] at [place], or null when a grant of the code anywhere counts.
     * The web's copy of the old grant rules (`web/src/lib/grants.ts`) still reads it, through `WebAccessTableTest`.
     */
    fun contextsFor(
        reach: Reach,
        place: Place,
    ): List<Pair<CapabilityContextType, UUID>>? {
        val home = place.branchId?.let { CapabilityContextType.BRANCH to it }
        val everywhere = CapabilityContextType.GLOBAL to CapabilityService.GLOBAL_CONTEXT_ID
        val day = (place as? Place.Day)?.let { CapabilityContextType.BRANCH_DAY to it.dayId }
        return when (reach) {
            Reach.ANYWHERE -> null
            Reach.EVERYWHERE -> listOf(everywhere)
            Reach.HOME -> listOfNotNull(home)
            Reach.HOME_OR_EVERYWHERE -> listOfNotNull(home, everywhere)
            Reach.HOME_OR_DAY -> listOfNotNull(home, day)
            Reach.HOME_OR_EVERYWHERE_OR_DAY -> listOfNotNull(home, everywhere, day)
        }
    }

    fun allows(
        userId: UUID,
        act: Act,
        place: Place = Place.Everywhere,
    ): Boolean {
        val contexts =
            contextsFor(act.reach, place) ?: return CapabilityRepository.hasCapabilityAnyContext(userId, act.code)
        return CapabilityRepository.holdsIn(userId, act.code, contexts)
    }

    fun require(
        userId: UUID,
        act: Act,
        place: Place = Place.Everywhere,
        message: String = defaultMessage(act),
    ) {
        if (!allows(userId, act, place)) throw ForbiddenException(message)
    }

    /** A read (GET) of an act's routes passes for whoever may see the place ([Act.whenReading]) or do the act. */
    fun require(
        context: Context,
        act: Act,
        place: Place = Place.Everywhere,
        message: String = defaultMessage(act),
    ) {
        val userId = context.callerUuid()
        val seeing = act.whenReading
        if (context.method() !in READS || seeing == act) return require(userId, act, place, message)
        if (!allows(userId, seeing, place) &&
            !allows(userId, act, place)
        ) {
            throw ForbiddenException(defaultMessage(seeing))
        }
    }

    /**
     * "You only get paid if you belong to that branch or you were relief
     * that day. With or without clock in." So [payee], whoever pays them, must work at [day]: a member,
     * a helper, or relief granted for it, the same grants [Act.WORK_DAY] counts.
     */
    fun requirePayable(
        payee: UUID,
        day: Place.Day,
    ) {
        if (!allows(payee, Act.WORK_DAY, day)) {
            throw ForbiddenException("Only this branch's staff, or that day's relief, can be paid for this day")
        }
    }

    private val READS = setOf(HandlerType.GET, HandlerType.HEAD)

    private fun defaultMessage(act: Act): String =
        when (act.reach) {
            Reach.EVERYWHERE, Reach.ANYWHERE -> {
                "${act.code} capability required"
            }

            Reach.HOME, Reach.HOME_OR_EVERYWHERE -> {
                "${act.code} capability required for this branch"
            }

            Reach.HOME_OR_DAY, Reach.HOME_OR_EVERYWHERE_OR_DAY -> {
                "${act.code} capability required for this branch or day"
            }
        }
}
