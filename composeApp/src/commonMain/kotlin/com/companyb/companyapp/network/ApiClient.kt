package com.companyb.companyapp.network

import com.companyb.companyapp.config.MAX_HTTP_RETRIES
import com.companyb.companyapp.config.platformDefaultBaseUrl
import com.companyb.companyapp.util.logInfo
import io.ktor.client.HttpClient
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
) {
    val onUnauthorized: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)

    val httpClient =
        HttpClient {
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
                level = LogLevel.HEADERS
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
                        !it.url.toString().contains("/auth/")
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
                        logInfo("ApiClient", "401 detected (HTTP 401)")
                        onUnauthorized.tryEmit(Unit)
                    }
                }
            }
        }
}
