package com.companyb.companyapp.identity

/**
 * Purpose discriminator for credential-token rows (#350). INVITE backs the admin-minted
 * account-setup link; PASSWORD_RESET is reserved for the forgot-password flow (#353),
 * which reuses the same storage.
 *
 * Backend-only (#566): never serialized over the wire, persisted as the
 * `credential_purpose` Postgres enum via [CredentialTokenTable]. Moved out of shared
 * so shared holds only real cross-target contracts.
 */
enum class CredentialTokenPurpose { INVITE, PASSWORD_RESET }
