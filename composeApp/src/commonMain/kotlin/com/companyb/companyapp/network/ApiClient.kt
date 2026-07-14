package com.companyb.companyapp.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json

expect val defaultBaseUrl: String

class ApiClient(
    private val tokenStore: TokenStore,
    baseUrl: String = defaultBaseUrl,
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

            install(Auth) {
                bearer {
                    loadTokens {
                        tokenStore.getToken()?.let { BearerTokens(it, "") }
                    }
                    sendWithoutRequest {
                        !it.url.toString().contains("/auth/")
                    }
                }
            }

            install(HttpRequestRetry) {
                maxRetries = 3
                retryOnServerErrors(3)
            }

            install(DefaultRequest) {
                url(baseUrl)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }

            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        onUnauthorized.tryEmit(Unit)
                    }
                }
            }
        }
}
