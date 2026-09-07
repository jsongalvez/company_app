package com.companyb.companyapp.http.openapi

import com.companyb.companyapp.contracts.workforce.ClockInResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.registerAllRoutes
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #495 — compiled route declarations and Kotlin serializers own the API contract.
 * DB-free: exercises the production route registrar, the serializer projector,
 * representative payloads through [KotlinxSerializationMapper], and the packaged
 * kapt resource without database-dependent business requests.
 */
class OpenApiContractTest {
    private val mapper = ObjectMapper()
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private fun canonicalDocument(): com.fasterxml.jackson.databind.node.ObjectNode {
        val input = OpenApiCanonical.kaptResourceText()
        val tree = mapper.readTree(OpenApiCanonical.canonicalize(input))
        // SAFETY: canonicalize always emits a JSON object document #495
        return tree as com.fasterxml.jackson.databind.node.ObjectNode
    }

    private fun schema(name: String): com.fasterxml.jackson.databind.node.ObjectNode =
        DtoSchemaProjector.projectAll()[name] ?: error("Missing $name")

    private fun propertiesOf(name: String): com.fasterxml.jackson.databind.node.ObjectNode {
        val props = schema(name).get("properties") as? com.fasterxml.jackson.databind.node.ObjectNode
        return props ?: error("Missing properties for $name")
    }

    private fun requiredOf(name: String): Set<String> =
        schema(name).get("required")?.map { it.asText() }?.toSet() ?: emptySet()

    private fun registeredRoutes(): Set<String> {
        val seen = mutableSetOf<String>()
        val app =
            io.javalin.Javalin.create { cfg ->
                cfg.events.handlerAdded { meta ->
                    if (meta.httpMethod.isHttpMethod) {
                        val path = meta.path
                        if (!path.startsWith("/openapi") && !path.startsWith("/swagger") &&
                            !path.startsWith("/redoc")
                        ) {
                            seen.add("${meta.httpMethod.name} $path")
                        }
                    }
                }
                registerAllRoutes(cfg)
            }
        app.stop()
        return seen
    }

    @Test
    fun `every registered product operation is documented exactly once`() {
        val registered = registeredRoutes()
        assertTrue(registered.size >= 130, "Expected the full product route set, got ${registered.size}")
        val document = canonicalDocument()
        val paths = document.get("paths")
        val documented = mutableSetOf<String>()
        val operationIds = mutableSetOf<String>()
        for (path in paths.properties()) {
            for (method in (path.value as com.fasterxml.jackson.databind.node.ObjectNode).properties()) {
                val operation = method.value
                val key = "${method.key.uppercase()} ${path.key}"
                // Javalin registers paths with {param}; the document uses the same form.
                documented.add(key)
                // Method names in the document are lowercase (get/post/...).
                val normalizedKey = "${method.key.uppercase()} ${path.key}"
                assertTrue(normalizedKey.isNotEmpty())
                val operationId = operation.get("operationId")?.asText()
                assertNotNull(operationId, "Missing operationId for $key")
                assertTrue(operationIds.add(operationId), "Duplicate operationId: $operationId")
                val responses = operation.get("responses")
                assertNotNull(responses, "Missing responses for $key")
                assertTrue(responses.size() > 0, "No responses for $key")
            }
        }
        val missing = registered - documented
        val extra = documented - registered
        assertTrue(missing.isEmpty(), "Registered routes without documentation: $missing")
        assertTrue(extra.isEmpty(), "Documented operations without registration: $extra")
    }

    @Test
    fun `protected operations are secured and public operations are open`() {
        val document = canonicalDocument()
        val paths = document.get("paths") as com.fasterxml.jackson.databind.node.ObjectNode
        for (path in paths.properties()) {
            for (method in (path.value as com.fasterxml.jackson.databind.node.ObjectNode).properties()) {
                val operation = method.value
                if (path.key.startsWith("/api/")) {
                    val security = operation.get("security")
                    assertNotNull(
                        security,
                        "Protected ${method.key.uppercase()} ${path.key} must declare bearer security",
                    )
                    assertTrue(security.size() > 0)
                } else {
                    assertNull(
                        operation.get("security"),
                        "Public ${method.key.uppercase()} ${path.key} must not inherit bearer security",
                    )
                }
            }
        }
    }

    @Test
    fun `login invalid-credential and rate-limit branches are bodyless`() {
        val document = canonicalDocument()
        val login = document.get("paths").get("/auth/login").get("post")
        assertNull(login.get("responses").get("401").get("content"), "Login 401 must not carry generic ErrorResponse")
        assertNull(login.get("responses").get("429").get("content"), "Login 429 must not carry generic ErrorResponse")
        assertNotNull(login.get("responses").get("200").get("content"), "Login 200 must carry LoginResponse")
    }

    @Test
    fun `bodyless statuses stay bodyless and binary exports stay binary`() {
        val document = canonicalDocument()
        val paths = document.get("paths")
        val logout = paths.get("/api/auth/logout").get("post")
        assertNull(logout.get("responses").get("200").get("content"), "Logout 200 is bodyless")
        val daily = paths.get("/api/branches/{branchId}/export/daily").get("get")
        val content = daily.get("responses").get("200").get("content")
        assertNotNull(content, "Export daily 200 must declare binary content")
        assertNotNull(content.get("application/octet-stream"), "Export daily must be octet-stream")
    }

    @Test
    fun `serializer schemas preserve is-prefixes`() {
        val props = propertiesOf("ClockInResponse")
        assertNotNull(props.get("isRelief"), "isRelief must survive (JavaBean strips to relief)")
        assertNull(props.get("relief"), "Stripped relief must not exist")
    }

    @Test
    fun `serializer schemas preserve default omission and nullable-required`() {
        val createRequired = requiredOf("CreateClientRequest")
        // middleName has a default so callers may omit it.
        assertTrue("middleName" !in createRequired, "Defaulted middleName must be omittable")
        val clientRequired = requiredOf("ClientResponse")
        // ClientResponse.middleName is nullable with no default: still required, explicitly null.
        assertTrue("middleName" in clientRequired, "Nullable middleName without default stays required")
        val clockOutRequired = requiredOf("ClockOutResponse")
        // clockOut is nullable with no default: still required, explicitly null when open.
        assertTrue("clockOut" in clockOutRequired, "Nullable clockOut without default stays required")
    }

    @Test
    fun `serializer schemas preserve enums nested lists and string money`() {
        val sessions = propertiesOf("DashboardResponse").get("sessions")
        assertNotNull(sessions)
        assertEquals("array", sessions.get("type").asText())
        val amount = propertiesOf("CompensationResponse").get("amount")
        assertNotNull(amount)
        assertEquals("string", amount.get("type").asText(), "Money stays a wire string")
    }

    @Test
    fun `representative payloads round-trip through the runtime mapper`() {
        val payload =
            ClockInResponse(
                id = "11111111-1111-1111-1111-111111111111",
                branchDayId = "22222222-2222-2222-2222-222222222222",
                userId = "33333333-3333-3333-3333-333333333333",
                markedBy = "44444444-4444-4444-4444-444444444444",
                clockIn = "2026-09-05T00:00:00Z",
                clockOut = null,
                isRelief = true,
            )
        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("isRelief"), "Wire name keeps is-prefix, got: $encoded")
        val decoded = json.decodeFromString<ClockInResponse>(encoded)
        assertEquals(payload, decoded)
        // A payload contradicting serialization (missing required id) fails closed.
        assertFailsWith<SerializationException> {
            json.decodeFromString<ClockInResponse>(
                """{"branchDayId":"x","userId":"y","markedBy":"z","clockIn":"t","isRelief":false}""",
            )
        }
    }

    @Test
    fun `error payload shape matches the documented error schema`() {
        val encoded = json.encodeToString(ErrorResponse("boom"))
        assertTrue(encoded.contains("error"))
        assertTrue("error" in requiredOf("ErrorResponse"))
    }

    @Test
    fun `packaged resource smoke path resolves`() {
        val document = canonicalDocument()
        assertTrue(document.get("paths").size() > 0)
    }

    @Test
    fun `projected schemas never collapse to generic objects`() {
        for ((name, schema) in DtoSchemaProjector.projectAll()) {
            if (schema.get("type")?.asText() == "object") {
                assertNotNull(schema.get("properties"), "Object schema $name must declare properties")
            }
        }
    }

    @Test
    fun `unmapped live refs fail the contract`() {
        val ref = mapper.createObjectNode().put("\$ref", "#/components/schemas/Nope")
        val schema = mapper.createObjectNode()
        schema.set<com.fasterxml.jackson.databind.JsonNode>("schema", ref)
        val media = mapper.createObjectNode()
        media.set<com.fasterxml.jackson.databind.JsonNode>("application/json", schema)
        val ok = mapper.createObjectNode()
        ok.set<com.fasterxml.jackson.databind.JsonNode>("content", media)
        val responses = mapper.createObjectNode()
        responses.set<com.fasterxml.jackson.databind.JsonNode>("200", ok)
        val get = mapper.createObjectNode()
        get.put("operationId", "x")
        get.set<com.fasterxml.jackson.databind.JsonNode>("responses", responses)
        val path = mapper.createObjectNode()
        path.set<com.fasterxml.jackson.databind.JsonNode>("get", get)
        val paths = mapper.createObjectNode()
        paths.set<com.fasterxml.jackson.databind.JsonNode>("/api/x", path)
        val document = mapper.createObjectNode()
        document.put("openapi", "3.1.0")
        document.set<com.fasterxml.jackson.databind.JsonNode>("paths", paths)
        document.set<com.fasterxml.jackson.databind.JsonNode>("components", mapper.createObjectNode())
        assertFailsWith<IllegalStateException> {
            OpenApiCanonical.canonicalize(mapper.writeValueAsString(document))
        }
    }
}
