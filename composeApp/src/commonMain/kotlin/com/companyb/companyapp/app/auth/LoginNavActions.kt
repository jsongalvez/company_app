package com.companyb.companyapp.app.auth

/**
 * The login screen's navigation exits (#94/#350/#353): where to go on success and the optional
 * public-flow entries — bundled to keep the [LoginScreen] signature lean.
 */
data class LoginNavActions(
    val onLoginSuccess: () -> Unit,
    // #350 — entry into the public invite-redemption flow (optional: nav hosts without the
    // route registered keep the plain form).
    val onAcceptInviteClick: () -> Unit = {},
    // #353 — entry into the public forgot-password flow.
    val onForgotPasswordClick: () -> Unit = {},
)
