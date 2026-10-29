package com.companyb.companyapp.remittance

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Remittance-owned HTTP authorization composition.
 *
 * Resolves the remittance record, then delegates to the common
 * branch-scope check on [CapabilityFilter]. Authorization keeps
 * capability-context evaluation; how a remittance resolves that scope lives
 * here, next to the routes that need it.
 */
object RemittanceAuthz {
    /**
     * [Access.require] for [act] at the remittance's branch.
     * Throws NotFoundResponse (404) when the remittance does not exist.
     */
    fun requireForRemittance(
        context: Context,
        remittanceId: UUID,
        act: Act,
    ) {
        val remittance =
            RemittanceReads.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")
        Access.require(context, act, Place.Branch(remittance.branchId))
    }
}
