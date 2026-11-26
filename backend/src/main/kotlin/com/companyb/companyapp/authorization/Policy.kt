package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.identity.DelegateSeatChoice
import com.companyb.companyapp.contracts.workforce.DelegateRole
import java.util.UUID

private const val VIEW = CapabilityCodes.VIEW_BRANCH_DATA
private const val EDIT = CapabilityCodes.EDIT_BRANCH_DATA
private const val PAST_DAY = CapabilityCodes.EDIT_PAST_DAY
private const val VOID = CapabilityCodes.VOID_SESSION
private const val REMIT = CapabilityCodes.SUBMIT_REMITTANCE
private const val PAY = CapabilityCodes.ASSIGN_COMPENSATION
private const val STOCK = CapabilityCodes.MANAGE_PRODUCTS
private const val CATALOG = CapabilityCodes.MANAGE_CATALOG
private const val USERS = CapabilityCodes.MANAGE_USERS
private const val HELPERS = CapabilityCodes.ASSIGN_DELEGATE
private const val COMMISSION = CapabilityCodes.VIEW_COMMISSION
private const val ERASE = CapabilityCodes.ERASE_CLIENT
private const val ALERTS = CapabilityCodes.RECEIVE_NEXT_APPOINTMENT_ALERTS

private val MISSIONS_AND_TOURS = setOf(BranchType.MEDICAL_MISSION, BranchType.PROVINCIAL_TOUR)

/**
 * The roles, as `role.name` stores them, and what each grants. [everywhere] holds at every branch
 * at once. [atBranch] holds at each branch the person works at: one they are assigned to, or any
 * branch of a type in [worksUnassignedAt]. Someone holding a role that [works] is also a member
 * where they work ([Policy.MEMBER]). A change to a role is an edit here; `AccessMatrixTest` shows
 * what it changes.
 */
enum class Role(
    /** The app word (CONTEXT.md), what every screen shows for this role. */
    val word: String,
    val everywhere: Set<String>,
    val atBranch: Set<String>,
    val worksUnassignedAt: Set<BranchType> = emptySet(),
    val works: Boolean = true,
) {
    SUPERUSER(
        word = "Superuser",
        everywhere = setOf(VIEW, PAY, USERS, HELPERS, CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAST_DAY, VOID, REMIT, PAY, STOCK, COMMISSION),
        worksUnassignedAt = BranchType.entries.toSet(),
    ),
    OWNER(
        word = "Owner",
        everywhere = setOf(VIEW, PAY, USERS, HELPERS, CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAY, STOCK),
        worksUnassignedAt = MISSIONS_AND_TOURS,
    ),
    MANAGER(
        word = "Manager",
        everywhere = setOf(USERS, HELPERS, CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAST_DAY, VOID, REMIT, PAY, STOCK),
        worksUnassignedAt = MISSIONS_AND_TOURS,
    ),
    COORDINATOR(
        word = "Coordinator",
        everywhere = setOf(CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAST_DAY, VOID, REMIT, PAY, STOCK, ALERTS),
    ),
    PRACTITIONER(
        word = "Practitioner",
        everywhere = setOf(CATALOG),
        atBranch = setOf(VIEW, EDIT),
    ),
    ACCOUNTANT(
        word = "Accountant",
        everywhere = setOf(VIEW),
        atBranch = setOf(VIEW),
    ),
    ONBOARDING(
        word = "New (no access yet)",
        everywhere = emptySet(),
        atBranch = emptySet(),
        works = false,
    ),
    ;

    /** What the roles list shows for this role. */
    val codes: Set<String> get() = everywhere + atBranch

    companion object {
        fun parse(name: String): Role = valueOf(name)
    }
}

/** One grant: [code] held in one context, from one source. */
data class Grant(
    val code: String,
    val contextType: CapabilityContextType,
    val contextId: UUID,
    val sourceType: CapabilitySourceType,
    val priority: Short,
)

/**
 * What the database says about one person. [unassignedBranches] are the branches some role of
 * theirs works at without an assignment, with the roles that reach each. [reliefDays] are the branch
 * days they work as relief, [helperSeats] the branches they help at, with the role they work under there.
 */
data class Facts(
    val active: Boolean,
    val roles: Set<Role>,
    val homes: Set<UUID>,
    val unassignedBranches: Map<UUID, Set<Role>>,
    val reliefDays: Set<UUID>,
    val helperSeats: Map<UUID, Set<DelegateRole>>,
)

object Policy {
    /** What every working person holds where they work. */
    val MEMBER: Set<String> = setOf(VIEW, EDIT, VOID, COMMISSION)

    /** What a helper holds at their mission or tour, by the role they work under there. */
    val HELPER: Map<DelegateRole, Set<String>> =
        mapOf(
            DelegateRole.PRACTITIONER to MEMBER,
            DelegateRole.COORDINATOR to MEMBER + setOf(PAST_DAY, REMIT, PAY, STOCK, ALERTS),
        )

    /** The helper roles whose people get next-appointment alerts at the branch they help. */
    val ALERTED_HELPERS: Set<DelegateRole> = HELPER.filterValues { ALERTS in it }.keys

    /** Below relief and helper grants ([GrantPriorities]), so a code held both ways names relief or the seat. */
    const val ROLE_PRIORITY: Short = 5

    /**
     * Every grant [facts] give, one per code and context, the highest priority winning. A relief day
     * carries a member's rights, a helper seat [HELPER]'s.
     */
    fun grants(facts: Facts): List<Grant> {
        if (!facts.active) return emptyList()
        val everywhere =
            facts.roles.flatMap { it.everywhere }.map {
                roleGrant(
                    it,
                    CapabilityContextType.GLOBAL,
                    GLOBAL,
                )
            }
        val homes = facts.homes.flatMap { branch -> atBranch(facts.roles, branch) }
        val unassigned = facts.unassignedBranches.flatMap { (branch, roles) -> atBranch(roles, branch) }
        val relief = facts.reliefDays.flatMap { day -> MEMBER.map { reliefGrant(it, day) } }
        val helping =
            facts.helperSeats.flatMap { (branch, roles) ->
                roles.flatMap { HELPER.getValue(it) }.map { helperGrant(it, branch) }
            }
        return (relief + helping + everywhere + homes + unassigned)
            .groupBy { Triple(it.code, it.contextType, it.contextId) }
            .map { (_, same) -> same.maxBy { it.priority } }
    }

    private fun atBranch(
        roles: Set<Role>,
        branch: UUID,
    ): List<Grant> {
        val member = if (roles.any { it.works }) MEMBER else emptySet()
        return (roles.flatMap { it.atBranch } + member).map { roleGrant(it, CapabilityContextType.BRANCH, branch) }
    }

    private fun helperGrant(
        code: String,
        branch: UUID,
    ) = Grant(
        code,
        CapabilityContextType.BRANCH,
        branch,
        CapabilitySourceType.MEDICAL_MISSION_DELEGATE,
        GrantPriorities.MEDICAL_MISSION_DELEGATE,
    )

    private fun reliefGrant(
        code: String,
        day: UUID,
    ) = Grant(
        code,
        CapabilityContextType.BRANCH_DAY,
        day,
        CapabilitySourceType.RELIEF_ACCESS,
        GrantPriorities.RELIEF_ACCESS,
    )

    private fun roleGrant(
        code: String,
        type: CapabilityContextType,
        at: UUID,
    ) = Grant(code, type, at, CapabilitySourceType.ROLE, ROLE_PRIORITY)

    private val GLOBAL: UUID = CapabilityService.GLOBAL_CONTEXT_ID

    /** The roles whose people get next-appointment alerts at the branches they are assigned to. */
    val ALERTED_ROLES: Set<Role> = Role.entries.filter { ALERTS in it.atBranch }.toSet()
}

/**
 * The roles the user screens treat apart, and who may change whose roles or account: only an Owner
 * or a Superuser gives or takes away Owner or Manager, nobody changes their own roles, only a
 * Superuser changes or deactivates a Superuser, and only an Owner or a Superuser deactivates an Owner.
 * Each check returns the refusal, or null when the change may go ahead. Names are `role.name` values.
 */
object RoleRules {
    /** Given and taken away only in the database, never through the API; the only role that views as someone else. */
    val TOP: String = Role.SUPERUSER.name

    /** What [TOP] is shown as to someone who does not hold it. */
    val TOP_SHOWN_AS: String = Role.PRACTITIONER.name

    /** No access yet: someone holding only this is not staff. */
    val NO_ACCESS: String = Role.ONBOARDING.name

    /** Never has a home branch and never helps. */
    val NO_BRANCH: String = Role.ACCOUNTANT.name

    /** A helper's role when none is picked. */
    val DEFAULT_HELPER: DelegateRole = DelegateRole.PRACTITIONER

    /** The app word for a role name; a name no role has reads as itself. */
    fun word(name: String): String = Role.entries.firstOrNull { it.name == name }?.word ?: name

    /** The Roles dialog offers every role but [TOP] (database only) and [NO_ACCESS] (what an empty pick saves). */
    fun assignable(name: String): Boolean = name != TOP && name != NO_ACCESS

    /** Whether a helper may be given [name], and whether it is [DEFAULT_HELPER]. */
    fun helperChoice(name: String): DelegateSeatChoice? =
        when {
            name == DEFAULT_HELPER.name -> DelegateSeatChoice.DEFAULT
            DelegateRole.entries.any { it.name == name } -> DelegateSeatChoice.OFFERED
            else -> null
        }

    /** A viewer holding [TOP] may view as anyone who doesn't. */
    fun mayViewAs(
        viewerIsTop: Boolean,
        targetRoles: Collection<String>,
    ): Boolean = viewerIsTop && TOP !in targetRoles

    private val RANKS = setOf(Role.OWNER.name, Role.MANAGER.name)
    private val ABOVE_RANKS = setOf(Role.OWNER.name, TOP)

    /**
     * The roles a viewer is shown for someone holding [roles]:
     * [TOP] shows as [TOP_SHOWN_AS] unless the viewer holds it.
     */
    fun shownTo(
        roles: List<String>,
        viewerIsTop: Boolean,
    ): List<String> = if (viewerIsTop || TOP !in roles) roles else (roles - TOP + TOP_SHOWN_AS).distinct().sorted()

    /**
     * The roles to save when someone who was shown [before] through [shownTo] asks for [requested]:
     * the [TOP] role stays, and a [TOP_SHOWN_AS] the target never had is the mask, not a new role.
     */
    fun unmasked(
        callerRoles: Collection<String>,
        before: Collection<String>,
        requested: List<String>,
    ): List<String> =
        if (TOP in before && TOP !in callerRoles) {
            requested.filterNot { it == TOP_SHOWN_AS && it !in before } + TOP
        } else {
            requested
        }

    fun roleChange(
        self: Boolean,
        callerRoles: Collection<String>,
        before: Collection<String>,
        after: Collection<String>,
    ): String? {
        val changed = (before.toSet() - after.toSet()) + (after.toSet() - before.toSet())
        return when {
            changed.isEmpty() -> {
                null
            }

            self -> {
                "You can't change your own roles"
            }

            TOP in before && TOP !in callerRoles -> {
                "Only a Superuser can change a Superuser"
            }

            changed.any { it in RANKS } && callerRoles.none { it in ABOVE_RANKS } -> {
                "Only an Owner can give or take away Owner or Manager"
            }

            else -> {
                null
            }
        }
    }

    fun statusChange(
        callerRoles: Collection<String>,
        targetRoles: Collection<String>,
    ): String? =
        when {
            TOP in targetRoles && TOP !in callerRoles -> {
                "Only a Superuser can change a Superuser's account"
            }

            Role.OWNER.name in targetRoles && callerRoles.none { it in ABOVE_RANKS } -> {
                "Only an Owner can change an Owner's account"
            }

            else -> {
                null
            }
        }
}
