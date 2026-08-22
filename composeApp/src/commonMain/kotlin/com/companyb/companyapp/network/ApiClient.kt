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
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json

class ApiClient(
    private val tokenStore: TokenStore,
    baseUrl: String = platformDefaultBaseUrl,
    engine: HttpClientEngine = httpClientEngine(),
) {
    // #94 Q3 — carries the 401'd request path so the App handler can discriminate credential
    // 401s (POST /auth/login → inline form error, no global reaction) from
    // session 401s (clear token → Login, "session expired" mid-session, silent at launch).
    val onUnauthorized: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)

    val httpClient: HttpClient =
        HttpClient(engine) {
            configure(tokenStore, baseUrl, onUnauthorized)
        }

    companion object {
        private fun HttpClientConfig<*>.configure(
            tokenStore: TokenStore,
            baseUrl: String,
            onUnauthorized: MutableSharedFlow<String>,
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

            install(Auth) {
                bearer {
                    loadTokens {
                        val token = tokenStore.getToken()
                        if (token != null) {
                            logInfo("ApiClient", "Token loaded (length=${token.length})")
                        } else {
                            logInfo("ApiClient", "Token not found in store")
                        }
                        token?.let { BearerTokens(it, "") }
                    }
                    sendWithoutRequest {
                        val url = it.url.toString()
                        url.endsWith(ApiRoutes.AUTH_LOGIN)
                    }
                }
            }

            install(HttpRequestRetry) {
                maxRetries = MAX_HTTP_RETRIES
            }

            install(DefaultRequest) {
                url(baseUrl)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }

            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        val path = response.call.request.url.encodedPath
                        logInfo("ApiClient", "401 detected (HTTP 401) on $path")
                        onUnauthorized.tryEmit(path)
                    }
                }
            }
        }
    }
}
