package com.companyb.companyapp.domain

/**
 * Purpose discriminator for credential-token rows (#350). INVITE backs the admin-minted
 * account-setup link; PASSWORD_RESET is reserved for the forgot-password flow (#353),
 * which reuses the same storage.
 */
enum class CredentialTokenPurpose { INVITE, PASSWORD_RESET }
