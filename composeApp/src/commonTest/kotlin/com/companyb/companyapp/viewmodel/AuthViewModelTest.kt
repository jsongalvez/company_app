package com.companyb.companyapp.viewmodel

import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.FakeTokenStore
import com.companyb.companyapp.network.mockEngine
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthViewModelTest {
    @Test
    fun `loginSuccess via apiClient`() =
        runTest {
            val engine =
                mockEngine(
                    status = HttpStatusCode.OK,
                    body = """{"token": "fake-jwt"}""",
                )
            val apiClient = ApiClient(tokenStore = FakeTokenStore(), engine = engine)
            val response =
                apiClient.httpClient.post("/auth/login") {
                    setBody("""{"username": "test", "password": "pass"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("fake-jwt"))
        }

    @Test
    fun `loginFailure via apiClient`() =
        runTest {
            val engine =
                mockEngine(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"error": "invalid"}""",
                )
            val apiClient = ApiClient(tokenStore = FakeTokenStore(), engine = engine)
            val response =
                apiClient.httpClient.post("/auth/login") {
                    setBody("""{"username": "test", "password": "wrong"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
