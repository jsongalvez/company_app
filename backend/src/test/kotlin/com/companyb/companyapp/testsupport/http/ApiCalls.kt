package com.companyb.companyapp.testsupport.http

import io.javalin.testtools.HttpClient
import io.javalin.testtools.Request
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.assertEquals

/** A request as [user], the header the test app reads. */
internal fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

internal fun HttpClient.expectGet(
    code: Int,
    path: String,
    user: UUID,
) {
    val response = get(path, asUser(user))
    assertEquals(code, response.code, "GET $path as $user: ${response.body.string().orEmpty()}")
}

internal fun HttpClient.expectPost(
    code: Int,
    path: String,
    body: Any,
    user: UUID,
) {
    val response = post(path, body, asUser(user))
    assertEquals(code, response.code, "POST $path as $user: ${response.body.string().orEmpty()}")
}

internal fun HttpClient.expectPatch(
    code: Int,
    path: String,
    body: Any,
    user: UUID,
) {
    val response = patch(path, body, asUser(user))
    assertEquals(code, response.code, "PATCH $path as $user: ${response.body.string().orEmpty()}")
}

internal fun HttpClient.expectDelete(
    code: Int,
    path: String,
    user: UUID,
) {
    val response = delete(path, "", asUser(user))
    assertEquals(code, response.code, "DELETE $path as $user: ${response.body.string().orEmpty()}")
}
