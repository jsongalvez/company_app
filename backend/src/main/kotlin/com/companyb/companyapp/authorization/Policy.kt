package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.branch.BranchType
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
    val everywhere: Set<String>,
    val atBranch: Set<String>,
    val worksUnassignedAt: Set<BranchType> = emptySet(),
    val works: Boolean = true,
) {
    SUPERUSER(
        everywhere = setOf(VIEW, PAY, USERS, HELPERS, CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAST_DAY, VOID, REMIT, PAY, STOCK, COMMISSION),
        worksUnassignedAt = BranchType.entries.toSet(),
    ),
    OWNER(
        everywhere = setOf(VIEW, PAY, USERS, HELPERS, CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAY, STOCK),
        worksUnassignedAt = MISSIONS_AND_TOURS,
    ),
    MANAGER(
        everywhere = setOf(USERS, HELPERS, CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAST_DAY, VOID, REMIT, PAY, STOCK),
        worksUnassignedAt = MISSIONS_AND_TOURS,
    ),
    COORDINATOR(
        everywhere = setOf(CATALOG, ERASE),
        atBranch = setOf(VIEW, EDIT, PAST_DAY, VOID, REMIT, PAY, STOCK, ALERTS),
    ),
    PRACTITIONER(
        everywhere = setOf(CATALOG),
        atBranch = setOf(VIEW, EDIT),
    ),
    ACCOUNTANT(
        everywhere = setOf(VIEW),
        atBranch = setOf(VIEW),
    ),
    ONBOARDING(
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
