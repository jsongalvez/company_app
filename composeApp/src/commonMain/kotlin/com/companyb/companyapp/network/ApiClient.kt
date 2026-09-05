package com.companyb.companyapp.network

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.config.MAX_HTTP_RETRIES
import com.companyb.companyapp.config.platformDefaultBaseUrl
import com.companyb.companyapp.util.logInfo
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json

class ApiClient(
    private val tokenStore: TokenStore,
    baseUrl: String = platformDefaultBaseUrl,
    engine: HttpClientEngine = httpClientEngine(),
) {
    /** #504 — 401 carries the credential that sent it so stale sessions never clear the current one. */
    data class UnauthorizedEvent(
        val path: String,
        val sentToken: String?,
    )

    // #94 Q3 + #504 — session 401s (clear → Login) discriminate from credential 401s
    // (public auth → inline error) and stale-session 401s (ignored).
    val onUnauthorized: MutableSharedFlow<UnauthorizedEvent> = MutableSharedFlow(extraBufferCapacity = 1)

    val httpClient: HttpClient =
        HttpClient(engine) {
            configure(tokenStore, baseUrl, onUnauthorized)
        }

    /** #504 — one session credential owner; no provider cache, no refresh: reads are always fresh. */
    fun setSessionToken(token: String) {
        tokenStore.saveToken(token)
    }

    fun clearSession() {
        tokenStore.clearToken()
    }

    fun currentToken(): String? = tokenStore.getToken()

    /** #504 — UI-facing store: direct writes route through the owner. */
    val sessionTokens: TokenStore =
        object : TokenStore {
            override fun saveToken(token: String) = setSessionToken(token)

            override fun getToken(): String? = currentToken()

            override fun clearToken() = clearSession()
        }

    /** #504 — only the live credential's non-public 401 invalidates the session. */
    fun isCurrentSessionEvent(event: UnauthorizedEvent): Boolean {
        if (isPublicAuthPath(event.path)) return false
        val current = currentToken()
        return event.sentToken != null && event.sentToken == current
    }

    companion object {
        /** #504 — public credential legs: never carry a bearer, never trip session expiry. */
        val publicAuthPaths =
            setOf(
                ApiRoutes.AUTH_LOGIN,
                ApiRoutes.AUTH_ACCEPT_INVITE,
                ApiRoutes.AUTH_FORGOT_PASSWORD,
                ApiRoutes.AUTH_RESET_PASSWORD,
            )

        fun isPublicAuthPath(path: String): Boolean = path in publicAuthPaths

        private fun isProtectedApiRequest(
            url: Url,
            base: Url,
        ): Boolean {
            if (url.protocol != base.protocol || url.host != base.host || url.port != base.port) return false
            if (url.encodedPath in publicAuthPaths) return false
            return url.encodedPath.startsWith(ApiRoutes.API_PREFIX)
        }

        private fun HttpClientConfig<*>.configure(
            tokenStore: TokenStore,
            baseUrl: String,
            onUnauthorized: MutableSharedFlow<UnauthorizedEvent>,
        ) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true
                    },
                )
            }

            install(Logging) {
                level = LogLevel.INFO
            }

            install(HttpRequestRetry) {
                maxRetries = MAX_HTTP_RETRIES
            }

            install(DefaultRequest) {
                url(baseUrl)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }

            // #504 — explicit bearer, no provider cache, no silent retry: the current
            // token rides preemptively only to the configured origin's protected paths.
            // Public auth legs and foreign origins never carry credentials, on any status.
            install(
                createClientPlugin("SessionBearer") {
                    onRequest { request, _ ->
                        try {
                            if (!isProtectedApiRequest(request.url.build(), Url(baseUrl))) return@onRequest
                            val token = tokenStore.getToken() ?: return@onRequest
                            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                        } catch (e: Exception) {
                            logInfo("ApiClient", "bearer attach skipped: ${e.message}")
                        }
                    }
                },
            )

            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        val path = response.call.request.url.encodedPath
                        val sentToken =
                            response.call.request.headers[HttpHeaders.Authorization]
                                ?.removePrefix("Bearer ")
                                ?.takeIf { it.isNotEmpty() }
                        logInfo("ApiClient", "401 detected (HTTP 401) on $path")
                        onUnauthorized.tryEmit(UnauthorizedEvent(path, sentToken))
                    }
                }
            }
        }
    }
}
