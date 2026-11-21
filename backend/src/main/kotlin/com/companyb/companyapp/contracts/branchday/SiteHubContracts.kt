package com.companyb.companyapp.contracts.branchday

import kotlinx.serialization.Serializable

/** An Owner or Manager pairs a site hub laptop; [name] is the laptop's own name, for the log. */
@Serializable
data class SiteHubLaptopRequest(
    val name: String,
)

/** The laptop's token: it lets the laptop prove hub.kiel.party for its certificate, until [expiresAt]. */
@Serializable
data class SiteHubLaptopResponse(
    val token: String,
    val expiresAt: String,
)

/** A laptop asks for (PUT) or drops (DELETE) the DNS-01 [value] of its certificate order. */
@Serializable
data class SiteHubChallengeRequest(
    val token: String,
    val value: String,
)

/** A fresh laptop token, so a laptop that renews on time never pairs again. */
@Serializable
data class SiteHubChallengeResponse(
    val token: String,
)
