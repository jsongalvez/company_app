package com.companyb.companyapp.api.routes

import com.companyb.companyapp.client.ClientRoutes
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.client.ClientResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Route validation for client routes. */
class ClientRouteValidationTest : RouteValidationBase() {
    private fun clientBody(vararg overrides: Pair<String, Any?>) =
        mapOf(
            "id" to uuid(),
            "firstName" to "John",
            "lastName" to "Doe",
            "gender" to "M",
            "age" to 25,
        ) + overrides

    // ──────────────────────────────────────────────
    // ClientRoutes
    // ──────────────────────────────────────────────

    @Test
    fun `POST client blank firstName returns 400`() = assertPost(400, "/api/clients", clientBody("firstName" to "  "))

    @Test
    fun `POST client blank lastName returns 400`() = assertPost(400, "/api/clients", clientBody("lastName" to "  "))

    @Test
    fun `POST client partial BP systolic only returns 400`() =
        assertPost(400, "/api/clients", clientBody("systolicBp" to 120))

    @Test
    fun `POST client partial BP diastolic only returns 400`() =
        assertPost(400, "/api/clients", clientBody("diastolicBp" to 80))

    @Test
    fun `POST client out-of-range age returns 400`() = assertPost(400, "/api/clients", clientBody("age" to 999))

    @Test
    fun `POST client out-of-range BP returns 400`() =
        assertPost(400, "/api/clients", clientBody("systolicBp" to 10, "diastolicBp" to 80))

    @Test
    fun `POST client overlong phone returns 400`() =
        assertPost(400, "/api/clients", clientBody("phoneNumber" to "123456789012345678901"))

    @Test
    fun `GET clients blank search query returns 400`() = assertGet(400, "/api/clients?q=%20%20")

    @Test
    fun `GET clients missing search query returns 400`() = assertGet(400, "/api/clients")

    @Test
    fun `GET clients includes authoritative session count`() {
        val response = testServer.client.get("/api/clients?q=Test")
        val clients = json.decodeFromString<List<ClientResponse>>(response.body.string())

        assertEquals(200, response.code)
        assertEquals(1, clients.single { it.id == testClientId.toString() }.sessionCount)
        assertEquals(0, clients.single { it.id == testClientNoSessionsId.toString() }.sessionCount)
    }

    @Test
    fun `PATCH client blank firstName returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("firstName" to "  "))

    @Test
    fun `PATCH client blank lastName returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("lastName" to "  "))

    @Test
    fun `PATCH client partial BP returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("systolicBp" to 120))

    @Test
    fun `PATCH client out-of-range age returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("age" to 999))

    @Test
    fun `PATCH client out-of-range BP returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("systolicBp" to 120, "diastolicBp" to 10))

    @Test
    fun `PATCH client overlong phone returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("phoneNumber" to "123456789012345678901"))

    @Test
    fun `PATCH client clear phone persists null`() {
        assertEquals(
            200,
            testServer.client.patch("/api/clients/$testClientId", mapOf("phoneNumber" to "09171234567")).code,
        )
        val response =
            testServer.client.patch(
                "/api/clients/$testClientId",
                mapOf("clearFields" to listOf("phoneNumber")),
            )
        assertEquals(200, response.code)
        val updated = json.decodeFromString<ClientResponse>(response.body.string())
        assertNull(updated.phoneNumber)
        assertEquals("Test", updated.firstName)
    }

    @Test
    fun `PATCH client clear BP pair persists nulls`() {
        val seeded =
            testServer.client.patch(
                "/api/clients/$testClientId",
                mapOf("systolicBp" to 120, "diastolicBp" to 80),
            )
        assertEquals(200, seeded.code)
        val response =
            testServer.client.patch(
                "/api/clients/$testClientId",
                mapOf("clearFields" to listOf("systolicBp", "diastolicBp")),
            )
        assertEquals(200, response.code)
        val updated = json.decodeFromString<ClientResponse>(response.body.string())
        assertNull(updated.systolicBp)
        assertNull(updated.diastolicBp)
    }

    @Test
    fun `PATCH client unknown clear field returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("nickname")))

    @Test
    fun `PATCH client clear required field returns 400`() {
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("firstName")))
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("age")))
    }

    @Test
    fun `PATCH client half BP clear returns 400`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("clearFields" to listOf("systolicBp")))

    @Test
    fun `PATCH client set and clear on the same field returns 400`() =
        assertPatch(
            400,
            "/api/clients/$testClientId",
            mapOf("phoneNumber" to "0917", "clearFields" to listOf("phoneNumber")),
        )

    @Test
    fun `PATCH client blank optional returns 400 without storing whitespace`() =
        assertPatch(400, "/api/clients/$testClientId", mapOf("phoneNumber" to "   "))

    @Test
    fun `POST anonymize client with pending session returns 409 and keeps PII`() {
        val response = testServer.client.post("/api/clients/$testClientId/anonymize")

        assertEquals(409, response.code)
        transaction {
            val row = ClientTable.selectAll().where { ClientTable.id eq testClientId }.single()
            assertEquals("Test", row[ClientTable.firstName])
            assertEquals("Client", row[ClientTable.lastName])
            assertNull(row[ClientTable.deletedAt])
        }
    }

    @Test
    fun `POST anonymize client without sessions returns 204`() {
        val response = testServer.client.post("/api/clients/$testClientNoSessionsId/anonymize")

        assertEquals(204, response.code)
        transaction {
            val row =
                ClientTable.selectAll().where { ClientTable.id eq testClientNoSessionsId }.single()
            assertNull(row[ClientTable.firstName])
            assertNull(row[ClientTable.lastName])
        }
    }
}
