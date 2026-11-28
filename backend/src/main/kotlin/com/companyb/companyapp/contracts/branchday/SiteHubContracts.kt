package com.companyb.companyapp.contracts.branchday

import kotlinx.serialization.Serializable

/** An active staff member pairs a site hub laptop; [name] is the laptop's hostname, for the registry. */
@Serializable
data class SiteHubLaptopRequest(
    val name: String,
)

/** The paired laptop's id and revocable token used to fetch the hub certificate. */
@Serializable
data class SiteHubLaptopResponse(
    val id: String,
    val token: String,
)

/** One paired site hub laptop shown to Owners and Managers for auditing or revocation. */
@Serializable
data class SiteHubLaptopSummary(
    val id: String,
    val name: String,
    val pairedBy: String,
    val pairedByName: String,
    val pairedAt: String,
    val lastSeenAt: String?,
    val revokedAt: String?,
    val revokedBy: String?,
)

/** Every paired site hub laptop, newest first. */
@Serializable
data class SiteHubLaptopsResponse(
    val laptops: List<SiteHubLaptopSummary>,
)

/** A paired laptop asks the server for the current `hub.kiel.party` TLS certificate and private key. */
@Serializable
data class SiteHubCertRequest(
    val token: String,
)

/**
 * The server-managed `hub.kiel.party` certificate chain and private key (PEM),
 * valid between [notBefore] and [notAfter].
 */
@Serializable
data class SiteHubCertResponse(
    val certPem: String,
    val keyPem: String,
    val notBefore: String,
    val notAfter: String,
)
