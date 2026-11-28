package com.companyb.companyapp.authorization

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import io.javalin.http.Context
import io.javalin.http.HandlerType
import java.time.LocalDate
import java.util.UUID

/** Where a grant stands from the place of an action: everywhere, at its branch, at its day, or somewhere else. */
enum class Standing {
    EVERYWHERE,
    HOME,
    DAY,
    ELSEWHERE,
    ;

    companion object {
        /** How [grant] stands from [place]. */
        fun of(
            grant: Grant,
            place: Place,
        ): Standing {
            val day = (place as? Place.Day)?.dayId
            return when {
                grant.contextType == CapabilityContextType.GLOBAL -> EVERYWHERE
                grant.contextType == CapabilityContextType.BRANCH && grant.contextId == place.branchId -> HOME
                grant.contextType == CapabilityContextType.BRANCH_DAY && grant.contextId == day -> DAY
                else -> ELSEWHERE
            }
        }
    }
}

private val EVERYWHERE = setOf(Standing.EVERYWHERE)
private val ANYWHERE = Standing.entries.toSet()
private val HOME = setOf(Standing.HOME)
private val HOME_OR_EVERYWHERE = setOf(Standing.HOME, Standing.EVERYWHERE)
private val HOME_OR_DAY = setOf(Standing.HOME, Standing.DAY)
private val HOME_EVERYWHERE_OR_DAY = setOf(Standing.HOME, Standing.EVERYWHERE, Standing.DAY)

/**
 * Every action the server gates, with the capability it needs and the grants that count. This is
 * the one place to read who may do what; `AccessTableTest` pins the outcome per role and day.
 * Day state (closed and remitted days) is checked on top by `BranchDayService`, using
 * [CHANGE_CLOSED_DAY]. A read (GET) of an edit route also passes on [whenReading]: whoever may see
 * the place may read what its members read there.
 */
enum class Act(
    val code: String,
    val counts: Set<Standing>,
    whenReading: Act? = null,
) {
    SEE_BRANCH(CapabilityCodes.VIEW_BRANCH_DATA, HOME_OR_EVERYWHERE),
    SEE_EVERY_BRANCH(CapabilityCodes.VIEW_BRANCH_DATA, EVERYWHERE),
    SEE_DAY(CapabilityCodes.VIEW_BRANCH_DATA, HOME_EVERYWHERE_OR_DAY),
    SEE_ANYWHERE(CapabilityCodes.VIEW_BRANCH_DATA, ANYWHERE),
    WORK_DAY(CapabilityCodes.EDIT_BRANCH_DATA, HOME_OR_DAY, SEE_DAY),
    WORK_HOME_BRANCH(CapabilityCodes.EDIT_BRANCH_DATA, HOME, SEE_BRANCH),
    WORK_ANYWHERE(CapabilityCodes.EDIT_BRANCH_DATA, ANYWHERE, SEE_ANYWHERE),
    VOID(CapabilityCodes.VOID_SESSION, HOME_OR_DAY),

    /** What members earned in commission: only the branch's members and its relief for the day. */
    SEE_COMMISSION(CapabilityCodes.VIEW_COMMISSION, HOME_OR_DAY),
    CHANGE_CLOSED_DAY(CapabilityCodes.EDIT_PAST_DAY, HOME),
    PAY(CapabilityCodes.ASSIGN_COMPENSATION, HOME_OR_EVERYWHERE),

    // The user: "Accountants see remittance lists (view only, any branch)"; so does anyone who sees every branch.
    SUBMIT_REMITTANCE(CapabilityCodes.SUBMIT_REMITTANCE, HOME, SEE_EVERY_BRANCH),
    MANAGE_STOCK(CapabilityCodes.MANAGE_PRODUCTS, HOME),
    MANAGE_CATALOG(CapabilityCodes.MANAGE_CATALOG, EVERYWHERE),
    MANAGE_USERS(CapabilityCodes.MANAGE_USERS, EVERYWHERE),

    /** "Erase personal details": Coordinator and up, member or not. */
    ERASE_CLIENT(CapabilityCodes.ERASE_CLIENT, EVERYWHERE),
    ASSIGN_DELEGATE(CapabilityCodes.ASSIGN_DELEGATE, EVERYWHERE),
    ;

    /** The act a GET of this act's routes asks for. */
    val whenReading: Act = whenReading ?: this
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
    fun allows(
        userId: UUID,
        act: Act,
        place: Place = Place.Everywhere,
    ): Boolean = allows(CapabilityRepository.grantsOf(userId), act, place)

    /** Whether [grants] let their holder do [act] at [place]; [Rights] answers the web with this. */
    fun allows(
        grants: List<Grant>,
        act: Act,
        place: Place,
    ): Boolean = grants.any { it.code == act.code && Standing.of(it, place) in act.counts }

    /** True when [userId] is an active staff member with a role (not onboarding or deactivated). */
    fun isStaff(userId: UUID): Boolean = CapabilityRepository.isStaff(userId)

    fun requireStaff(
        userId: UUID,
        message: String = "Active staff role required",
    ) {
        if (!isStaff(userId)) throw ForbiddenException(message)
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
        when {
            Standing.HOME !in act.counts || Standing.ELSEWHERE in act.counts -> "${act.code} capability required"
            Standing.DAY in act.counts -> "${act.code} capability required for this branch or day"
            else -> "${act.code} capability required for this branch"
        }
}
