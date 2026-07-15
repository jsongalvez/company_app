package com.companyb.companyapp.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

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
