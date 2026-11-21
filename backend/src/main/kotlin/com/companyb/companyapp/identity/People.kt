package com.companyb.companyapp.identity

import com.companyb.companyapp.contracts.identity.UserStatus
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Who counts as a working person. An invite mints an ACTIVE app_user before anyone owns it;
 * until the invitee accepts, that row holds an unconsumed INVITE token (a re-invite consumes the
 * old ones, so any unconsumed one is the live or lapsed link). Such a stand-in is nobody yet:
 * every roster, picker, audience or eligibility read uses [active] instead of the raw status.
 */
object People {
    /** The [AppUserTable] row is ACTIVE and not waiting on an invite. Correlates on [AppUserTable.id]. */
    fun active(): Op<Boolean> = (AppUserTable.status eq UserStatus.ACTIVE) and notExists(openInvites())

    private fun openInvites() =
        CredentialTokenTable
            .select(CredentialTokenTable.id)
            .where {
                (CredentialTokenTable.userId eq AppUserTable.id) and
                    (CredentialTokenTable.purpose eq CredentialTokenPurpose.INVITE) and
                    CredentialTokenTable.consumedAt.isNull()
            }
}
