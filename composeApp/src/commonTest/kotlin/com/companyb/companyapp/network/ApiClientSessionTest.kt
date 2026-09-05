package com.companyb.companyapp.network

import com.companyb.companyapp.api.ApiRoutes
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #504 — one session lifecycle: preemptive bearer, public/origin exclusion, atomic replace/clear, stale-401 ignore. */
class ApiClientSessionTest {
    private class StoringTokenStore(
        var stored: String? = null,
    ) : TokenStore {
        override fun saveToken(token: String) {
            this.stored = token
        }

        override fun getToken(): String? = stored

        override fun clearToken() {
            stored = null
        }
    }

    private fun sessionClient(
        store: StoringTokenStore,
        baseUrl: String = "http://api.test",
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: ((auth: String?) -> Unit)? = null,
    ): ApiClient {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    requestHandlers.add { request ->
                        onRequest?.invoke(request.headers[HttpHeaders.Authorization])
                        respond(
                            content = ByteReadChannel(""),
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    dispatcher = Dispatchers.Unconfined
                },
            )
        return ApiClient(tokenStore = store, baseUrl = baseUrl, engine = engine)
    }

    @Test
    fun protectedFirstRequestCarriesBearerPreemptively() =
        runTest {
            val store = StoringTokenStore()
            var sent: String? = "unset"
            val client = sessionClient(store, onRequest = { sent = it })
            client.setSessionToken("token-A")

            client.httpClient.get(ApiRoutes.ME)

            assertEquals("Bearer token-A", sent)
        }

    @Test
    fun allPublicAuthPathsSendNoBearer() =
        runTest {
            val store = StoringTokenStore()
            val seen = mutableListOf<String?>()
            val client = sessionClient(store, onRequest = { seen += it })
            client.setSessionToken("token-A")

            client.httpClient.post(ApiRoutes.AUTH_LOGIN) { setBody("{}") }
            client.httpClient.post(ApiRoutes.AUTH_ACCEPT_INVITE) { setBody("{}") }
            client.httpClient.post(ApiRoutes.AUTH_FORGOT_PASSWORD) { setBody("{}") }
            client.httpClient.post(ApiRoutes.AUTH_RESET_PASSWORD) { setBody("{}") }

            assertEquals(listOf<String?>(null, null, null, null), seen)
        }

    @Test
    fun foreignOriginSendsNoBearer() =
        runTest {
            val store = StoringTokenStore()
            var sent: String? = "unset"
            val client = sessionClient(store, onRequest = { sent = it })
            client.setSessionToken("token-A")

            client.httpClient.get("https://other.example/api/me")

            assertNull(sent)
        }

    @Test
    fun persistedTokenBootstrapsFirstRequest() =
        runTest {
            val store = StoringTokenStore(stored = "persisted")
            var sent: String? = null
            val client = sessionClient(store, onRequest = { sent = it })

            client.httpClient.get(ApiRoutes.ME)

            assertEquals("Bearer persisted", sent)
        }

    @Test
    fun tokenReplacementSendsLatestWithoutResurrection() =
        runTest {
            val store = StoringTokenStore()
            val seen = mutableListOf<String?>()
            val client = sessionClient(store, onRequest = { seen += it })

            client.setSessionToken("token-A")
            client.httpClient.get(ApiRoutes.ME)
            client.setSessionToken("token-B")
            client.httpClient.get(ApiRoutes.ME)

            assertEquals(listOf<String?>("Bearer token-A", "Bearer token-B"), seen)
        }

    @Test
    fun clearThenLoginNeverResurrectsOldCredential() =
        runTest {
            val store = StoringTokenStore()
            val seen = mutableListOf<String?>()
            val client = sessionClient(store, onRequest = { seen += it })

            client.setSessionToken("token-A")
            client.httpClient.get(ApiRoutes.ME)
            client.clearSession()
            client.httpClient.get(ApiRoutes.ME)
            client.setSessionToken("token-B")
            client.httpClient.get(ApiRoutes.ME)

            assertEquals(listOf<String?>("Bearer token-A", null, "Bearer token-B"), seen)
        }

    @Test
    fun staleSession401DoesNotClearLiveSession() =
        runBlocking {
            val store = StoringTokenStore()
            val client = sessionClient(store, status = HttpStatusCode.Unauthorized)
            client.setSessionToken("token-A")

            val firstEvent = async { client.onUnauthorized.first() }
            delay(50)
            client.httpClient.get(ApiRoutes.ME)
            val eventA = withTimeout(10000) { firstEvent.await() }
            assertEquals("/api/me", eventA.path)
            assertEquals("token-A", eventA.sentToken)
            assertTrue(client.isCurrentSessionEvent(eventA))

            client.setSessionToken("token-B")
            assertFalse(client.isCurrentSessionEvent(eventA))

            val secondEvent = async { client.onUnauthorized.first() }
            delay(50)
            client.httpClient.get(ApiRoutes.ME)
            val eventB = withTimeout(10000) { secondEvent.await() }
            assertEquals("token-B", eventB.sentToken)
            assertTrue(client.isCurrentSessionEvent(eventB))
        }

    @Test
    fun public401NeverInvalidatesSession() =
        runBlocking {
            val store = StoringTokenStore(stored = "token-A")
            val client = sessionClient(store, status = HttpStatusCode.Unauthorized)

            val event = async { client.onUnauthorized.first() }
            delay(50)
            client.httpClient.post(ApiRoutes.AUTH_LOGIN) { setBody("{}") }
            val login401 = withTimeout(10000) { event.await() }

            assertNull(login401.sentToken)
            assertFalse(client.isCurrentSessionEvent(login401))
            assertTrue(ApiClient.isPublicAuthPath(login401.path))
        }
}
