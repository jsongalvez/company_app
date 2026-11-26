package com.companyb.companyapp.remittance

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Remittance-owned HTTP authorization composition.
 *
 * Finds the place a route's `Gate.At` checks from the record it names; the gate asks [Access]
 * itself. How the record resolves that place lives here, next to the routes that need it.
 */
object RemittanceAuthz {
    /** The remittance's branch. Throws NotFoundResponse (404) when the remittance does not exist. */
    fun placeOf(remittanceId: UUID): Place.Branch {
        val remittance =
            RemittanceReads.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")
        return Place.Branch(remittance.branchId)
    }
}
