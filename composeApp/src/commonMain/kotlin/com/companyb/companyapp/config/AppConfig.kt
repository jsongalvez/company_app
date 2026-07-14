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

/** Application display name. */
const val APP_NAME = "CompanyApp"

/** Key used to store/retrieve the JWT token in platform secure storage. */
const val TOKEN_STORE_KEY = "companyapp_jwt_token"

/** Maximum HTTP retry attempts for transient network/server errors. */
const val MAX_HTTP_RETRIES = 3

/** Connection timeout in milliseconds. */
const val API_CONNECT_TIMEOUT_MS = 10_000L

/** Request timeout in milliseconds. */
const val API_REQUEST_TIMEOUT_MS = 30_000L

/** Socket timeout in milliseconds. */
const val API_SOCKET_TIMEOUT_MS = 30_000L
