package com.companyb.companyapp.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers

fun mockApiClient(
    status: HttpStatusCode = HttpStatusCode.OK,
    body: String = "",
): ApiClient =
    ApiClient(
        tokenStore = FakeTokenStore(),
        engine = mockEngine(status, body),
    )

// Handler-based overload for tests that need URL routing, request counting, or multi-response scenarios
// (e.g., poll loops, multi-endpoint ViewModels). The handler receives HttpRequestData so it can route by URL.
fun mockApiClient(handler: MockRequestHandler): ApiClient =
    ApiClient(
        tokenStore = FakeTokenStore(),
        engine = mockEngine(handler),
    )

class FakeTokenStore : TokenStore {
    override fun getToken(): String? = null

    override fun saveToken(token: String) {}

    override fun clearToken() {}
}

fun mockEngine(
    status: HttpStatusCode = HttpStatusCode.OK,
    body: String = "",
): HttpClientEngine =
    MockEngine { _ ->
        respond(
            content = ByteReadChannel(body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

// Handler-based overload — Lambdas passed here receive `HttpRequestData` so the test can route by URL,
// count hits (increment a shared counter), or serve different responses across iterations.
//
// Sets `MockEngineConfig.dispatcher = Dispatchers.Unconfined` to disable the default IO dispatcher
// roundtrip so tests using `runTest` + `Dispatchers.setMain(StandardTestDispatcher(testScheduler))` can
// drain reliably via `runCurrent()` without racing the real IO thread. The simple
// `mockEngine(status, body)` overload above keeps MockEngine's default IO dispatcher (suitable for
// tests using `runTest` + `.join()` / `Dispatchers.setMain(Unconfined)` where the .join() blocks the
// test thread until completion, not for poll-loop virtual-time control).
fun mockEngine(handler: MockRequestHandler): HttpClientEngine =
    MockEngine(
        MockEngineConfig().apply {
            requestHandlers.add(handler)
            dispatcher = Dispatchers.Unconfined
        },
    )
