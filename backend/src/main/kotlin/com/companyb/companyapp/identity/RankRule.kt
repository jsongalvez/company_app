package com.companyb.companyapp.identity

/**
 * Who may change whose roles or account (audit H1; the user, message 13675, accepted): only an
 * Owner or a Superuser gives or takes away Owner or Manager, nobody changes their own roles, only a
 * Superuser changes or deactivates a Superuser, and only an Owner or a Superuser deactivates an Owner.
 * Each check returns the refusal, or null when the change may go ahead.
 */
internal object RankRule {
    private const val OWNER = "OWNER"
    private const val MANAGER = "MANAGER"
    private const val SUPERUSER = "SUPERUSER"
    private val RANKS = setOf(OWNER, MANAGER)
    private val ABOVE_RANKS = setOf(OWNER, SUPERUSER)

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

            SUPERUSER in before && SUPERUSER !in callerRoles -> {
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
            SUPERUSER in targetRoles && SUPERUSER !in callerRoles -> {
                "Only a Superuser can change a Superuser's account"
            }

            OWNER in targetRoles && callerRoles.none { it in ABOVE_RANKS } -> {
                "Only an Owner can change an Owner's account"
            }

            else -> {
                null
            }
        }
}
