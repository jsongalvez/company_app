package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.branch.BranchType
import java.util.UUID

/** A job title that decides what someone may do. Stored by name in `role.name`; a person may hold several. */
enum class Role { SUPERUSER, OWNER, MANAGER, COORDINATOR, PRACTITIONER, ACCOUNTANT, ONBOARDING }

/** How someone stands at a branch, or at one day of it. */
enum class Standing {
    /** Works there: a home-branch assignment, or a role in [STAFF_WITHOUT_ASSIGNMENT] for the branch's type. */
    MEMBER,

    /** Added to a mission or tour by an Owner or Manager; works it under the role picked then. */
    HELPER,

    /** Granted that one day. */
    RELIEF,
}

/** One way to be allowed an action. A person's roles count at a place they stand at, a helper's picked role instead. */
sealed interface Rule {
    val roles: Set<Role>

    /** By role alone, wherever the action happens. */
    data class Everywhere(
        override val roles: Set<Role>,
    ) : Rule

    /** By a role that counts where the action happens, standing there in one of [standings]. */
    data class Here(
        override val roles: Set<Role>,
        val standings: Set<Standing>,
    ) : Rule

    /** By a role that counts at any place the person stands at in one of [standings], wherever the action happens. */
    data class Somewhere(
        override val roles: Set<Role>,
        val standings: Set<Standing>,
    ) : Rule
}

private val STAFF = setOf(Role.SUPERUSER, Role.OWNER, Role.MANAGER, Role.COORDINATOR, Role.PRACTITIONER)
private val SEES_EVERY_BRANCH = setOf(Role.SUPERUSER, Role.OWNER, Role.ACCOUNTANT)
private val KEEPS_THE_BOOKS = setOf(Role.SUPERUSER, Role.MANAGER, Role.COORDINATOR)
private val COORDINATOR_AND_UP = setOf(Role.SUPERUSER, Role.OWNER, Role.MANAGER, Role.COORDINATOR)
private val MANAGES_PEOPLE = setOf(Role.SUPERUSER, Role.OWNER, Role.MANAGER)
private val PAYS_ANYWHERE = setOf(Role.SUPERUSER, Role.OWNER)

private val HOME = setOf(Standing.MEMBER, Standing.HELPER)
private val ON_THE_DAY = setOf(Standing.MEMBER, Standing.HELPER, Standing.RELIEF)

/** Roles that are staff at every branch of these types with no assignment. */
val STAFF_WITHOUT_ASSIGNMENT: Map<Role, Set<BranchType>> =
    mapOf(
        Role.SUPERUSER to BranchType.entries.toSet(),
        Role.OWNER to setOf(BranchType.PROVINCIAL_TOUR, BranchType.MEDICAL_MISSION),
        Role.MANAGER to setOf(BranchType.PROVINCIAL_TOUR, BranchType.MEDICAL_MISSION),
    )

/**
 * Every action the server gates, and the rules that allow it: the one table of who may do what.
 * Day state (closed and remitted days) is checked on top by `BranchDayService`, using
 * [CHANGE_CLOSED_DAY]. A read (GET) of an edit route also passes on [whenReading].
 * [code] and [reach] feed the web's copy of the old grant rules until the web reads this table.
 */
enum class Act(
    val code: String,
    val reach: Reach,
    val rules: List<Rule>,
    whenReading: Act? = null,
) {
    SEE_BRANCH(
        CapabilityCodes.VIEW_BRANCH_DATA,
        Reach.HOME_OR_EVERYWHERE,
        listOf(Rule.Everywhere(SEES_EVERY_BRANCH), Rule.Here(STAFF, HOME)),
    ),
    SEE_EVERY_BRANCH(CapabilityCodes.VIEW_BRANCH_DATA, Reach.EVERYWHERE, listOf(Rule.Everywhere(SEES_EVERY_BRANCH))),
    SEE_DAY(
        CapabilityCodes.VIEW_BRANCH_DATA,
        Reach.HOME_OR_EVERYWHERE_OR_DAY,
        listOf(Rule.Everywhere(SEES_EVERY_BRANCH), Rule.Here(STAFF, ON_THE_DAY)),
    ),
    SEE_ANYWHERE(
        CapabilityCodes.VIEW_BRANCH_DATA,
        Reach.ANYWHERE,
        listOf(Rule.Everywhere(SEES_EVERY_BRANCH), Rule.Somewhere(STAFF, ON_THE_DAY)),
    ),
    WORK_DAY(CapabilityCodes.EDIT_BRANCH_DATA, Reach.HOME_OR_DAY, listOf(Rule.Here(STAFF, ON_THE_DAY)), SEE_DAY),
    WORK_HOME_BRANCH(CapabilityCodes.EDIT_BRANCH_DATA, Reach.HOME, listOf(Rule.Here(STAFF, HOME)), SEE_BRANCH),
    WORK_ANYWHERE(
        CapabilityCodes.EDIT_BRANCH_DATA,
        Reach.ANYWHERE,
        listOf(Rule.Somewhere(STAFF, ON_THE_DAY)),
        SEE_ANYWHERE,
    ),
    VOID(CapabilityCodes.VOID_SESSION, Reach.HOME_OR_DAY, listOf(Rule.Here(STAFF, ON_THE_DAY))),

    /** What members earned in commission: only the branch's staff and that day's relief. */
    SEE_COMMISSION(CapabilityCodes.VIEW_COMMISSION, Reach.HOME_OR_DAY, listOf(Rule.Here(STAFF, ON_THE_DAY))),
    CHANGE_CLOSED_DAY(CapabilityCodes.EDIT_PAST_DAY, Reach.HOME, listOf(Rule.Here(KEEPS_THE_BOOKS, HOME))),
    PAY(
        CapabilityCodes.ASSIGN_COMPENSATION,
        Reach.HOME_OR_EVERYWHERE,
        listOf(Rule.Everywhere(PAYS_ANYWHERE), Rule.Here(COORDINATOR_AND_UP, HOME)),
    ),

    /** Remittance lists are open to whoever sees every branch, view only. */
    SUBMIT_REMITTANCE(
        CapabilityCodes.SUBMIT_REMITTANCE,
        Reach.HOME,
        listOf(Rule.Here(KEEPS_THE_BOOKS, HOME)),
        SEE_EVERY_BRANCH,
    ),
    MANAGE_STOCK(CapabilityCodes.MANAGE_PRODUCTS, Reach.HOME, listOf(Rule.Here(COORDINATOR_AND_UP, HOME))),
    MANAGE_CATALOG(CapabilityCodes.MANAGE_CATALOG, Reach.EVERYWHERE, listOf(Rule.Everywhere(STAFF))),
    MANAGE_USERS(CapabilityCodes.MANAGE_USERS, Reach.EVERYWHERE, listOf(Rule.Everywhere(MANAGES_PEOPLE))),

    /** "Erase personal details": Coordinator and up, member or not. */
    ERASE_CLIENT(CapabilityCodes.ERASE_CLIENT, Reach.EVERYWHERE, listOf(Rule.Everywhere(COORDINATOR_AND_UP))),
    ASSIGN_DELEGATE(CapabilityCodes.ASSIGN_DELEGATE, Reach.EVERYWHERE, listOf(Rule.Everywhere(MANAGES_PEOPLE))),
    ;

    /** The act a GET of this act's routes asks for. */
    val whenReading: Act = whenReading ?: this
}

/** How someone stands at one place, and the roles that count there. */
data class Footing(
    val standing: Standing,
    val roles: Set<Role>,
)

/** What the database holds about one active person that decides their access. Inactive people have none. */
data class Facts(
    val roles: Set<Role>,
    /** Branch id to how the person stands there. */
    val branches: Map<UUID, List<Footing>>,
    /** Relief day id to its branch id. */
    val reliefDays: Map<UUID, UUID>,
) {
    fun footingsAt(place: Place): List<Footing> {
        val branch = place.branchId ?: return emptyList()
        val atBranch = branches[branch].orEmpty()
        val day = (place as? Place.Day)?.dayId ?: return atBranch
        return if (reliefDays[day] == branch) atBranch + Footing(Standing.RELIEF, roles) else atBranch
    }

    fun allFootings(): List<Footing> =
        branches.values.flatten() + reliefDays.values.map { Footing(Standing.RELIEF, roles) }

    companion object {
        val NONE = Facts(emptySet(), emptyMap(), emptyMap())
    }
}

object Policy {
    fun allows(
        facts: Facts,
        act: Act,
        place: Place,
    ): Boolean = act.rules.any { matches(it, facts, place) }

    private fun matches(
        rule: Rule,
        facts: Facts,
        place: Place,
    ): Boolean =
        when (rule) {
            is Rule.Everywhere -> facts.roles.any { it in rule.roles }
            is Rule.Here -> facts.footingsAt(place).any { counts(rule.roles, rule.standings, it) }
            is Rule.Somewhere -> facts.allFootings().any { counts(rule.roles, rule.standings, it) }
        }

    private fun counts(
        roles: Set<Role>,
        standings: Set<Standing>,
        footing: Footing,
    ): Boolean = footing.standing in standings && footing.roles.any { it in roles }
}
