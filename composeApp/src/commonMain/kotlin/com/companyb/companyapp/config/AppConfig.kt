package com.companyb.companyapp.config

/**
 * Platform-specific default base URL for the API server.
 *
 * Android emulator → http://10.0.2.2:3023
 * iOS simulator    → http://localhost:3023
 * Desktop (JVM)    → http://localhost:3023
 *
 * Override by passing a custom baseUrl to ApiClient(baseUrl = ...).
 */
expect val platformDefaultBaseUrl: String

/** Key used to store/retrieve the JWT token in platform secure storage. */
const val TOKEN_STORE_KEY = "companyapp_jwt_token"

/** Maximum HTTP retry attempts for transient network/server errors. */
const val MAX_HTTP_RETRIES = 3
