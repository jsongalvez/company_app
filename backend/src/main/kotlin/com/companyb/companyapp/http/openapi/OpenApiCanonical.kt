package com.companyb.companyapp.http.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.openapi.plugin.DefinitionProcessor

/**
 * #495 — canonical OpenAPI document owner. Starts from kapt's generated operation
 * document and replaces DTO component schemas from their actual serializers.
 * Production OpenApiPlugin and the Gradle export/contract check call this same
 * function. Descriptor field/enum names preserve `is`-prefixes; requiredness
 * follows `isElementOptional`; unknown kinds fail closed.
 */
object OpenApiCanonical {
    private val mapper = ObjectMapper()

    val processor: DefinitionProcessor = DefinitionProcessor { document -> canonicalize(document.toString()) }

    /** #495 — named classpath boundary for the kapt resource (ref #468). */
    fun kaptResourceText(): String {
        val stream =
            OpenApiCanonical::class.java.getResourceAsStream("/openapi-plugin/openapi-default.json")
                ?: error("kapt OpenAPI resource missing from classpath")
        return stream.bufferedReader().readText()
    }

    fun canonicalize(inputJson: String): String {
        // SAFETY: kapt always emits a JSON object document #495
        val document = mapper.readTree(inputJson) as ObjectNode
        ensureInfo(document)
        val schemas = ensureSchemas(document)
        val projected = DtoSchemaProjector.projectAll()
        validateCoverage(document, schemas, projected)
        for ((name, schema) in projected) {
            schemas.set<ObjectNode>(name, schema)
        }
        ensureHealthMetrics(document, mapper)
        fixSecurity(document)
        return mapper.writeValueAsString(document)
    }

    private fun ensureInfo(document: ObjectNode) {
        val info =
            (document.get("info") as? ObjectNode)
                ?: mapper.createObjectNode().also { document.set<ObjectNode>("info", it) }
        info.put("title", "CompanyApp Backend API")
        info.put("version", "1.0.0")
        val components =
            (document.get("components") as? ObjectNode)
                ?: mapper.createObjectNode().also { document.set<ObjectNode>("components", it) }
        val securitySchemes =
            (components.get("securitySchemes") as? ObjectNode)
                ?: mapper.createObjectNode().also { components.set<ObjectNode>("securitySchemes", it) }
        val bearer = mapper.createObjectNode()
        bearer.put("type", "http")
        bearer.put("scheme", "bearer")
        bearer.put("bearerFormat", "JWT")
        securitySchemes.set<ObjectNode>("BearerAuth", bearer)
    }

    private fun ensureSchemas(document: ObjectNode): ObjectNode {
        val components =
            (document.get("components") as? ObjectNode)
                ?: mapper.createObjectNode().also { document.set<ObjectNode>("components", it) }
        return (components.get("schemas") as? ObjectNode)
            ?: mapper.createObjectNode().also { components.set<ObjectNode>("schemas", it) }
    }

    private fun fixSecurity(document: ObjectNode) {
        // Protected operations stay explicitly secured; public ones must not inherit a global bearer requirement.
        val paths = document.get("paths") as? ObjectNode ?: return
        for (pathEntry in paths.properties()) {
            fixPathSecurity(pathEntry.key, pathEntry.value)
        }
    }

    // #495 — one path's operations share the same security rule.
    private fun fixPathSecurity(
        path: String,
        methods: com.fasterxml.jackson.databind.JsonNode?,
    ) {
        val operations = methods as? ObjectNode ?: return
        for (methodEntry in operations.properties()) {
            val operation = methodEntry.value as? ObjectNode ?: continue
            if (path.startsWith("/api/")) {
                if (operation.get("security") == null || operation.get("security").isEmpty) {
                    operation.set<ArrayNode>("security", bearerSecurity())
                }
            } else {
                operation.remove("security")
            }
        }
    }

    private fun bearerSecurity(): ArrayNode {
        val security = mapper.createArrayNode()
        security.add(mapper.createObjectNode().set<ObjectNode>("BearerAuth", mapper.createArrayNode()))
        return security
    }

    private fun ensureHealthMetrics(
        document: ObjectNode,
        mapper: ObjectMapper,
    ) {
        val paths = document.get("paths") as? ObjectNode ?: return
        val health = paths.get("/health")?.get("get") as? ObjectNode
        if (health != null) {
            val healthSchema = mapper.createObjectNode()
            healthSchema.put("type", "object")
            healthSchema.put("additionalProperties", false)
            val required = mapper.createArrayNode().add("status")
            healthSchema.set<ArrayNode>("required", required)
            val props = mapper.createObjectNode()
            val status = mapper.createObjectNode().put("type", "string")
            status.set<ArrayNode>("enum", mapper.createArrayNode().add("UP").add("DOWN"))
            props.set<ObjectNode>("status", status)
            props.set<ObjectNode>("error", mapper.createObjectNode().put("type", "string"))
            healthSchema.set<ObjectNode>("properties", props)
            val responses =
                health.get("responses") as? ObjectNode
                    ?: mapper.createObjectNode().also { health.set<ObjectNode>("responses", it) }
            for (statusCode in listOf("200", "503")) {
                val response =
                    responses.get(statusCode) as? ObjectNode
                        ?: mapper.createObjectNode().also { responses.set<ObjectNode>(statusCode, it) }
                val content = mapper.createObjectNode()
                val json = mapper.createObjectNode()
                json.set<ObjectNode>("schema", healthSchema)
                content.set<ObjectNode>("application/json", json)
                response.set<ObjectNode>("content", content)
            }
        }
        val metrics = paths.get("/metrics")?.get("get") as? ObjectNode
        if (metrics != null) {
            val responses =
                metrics.get("responses") as? ObjectNode
                    ?: mapper.createObjectNode().also { metrics.set<ObjectNode>("responses", it) }
            val response =
                responses.get("200") as? ObjectNode
                    ?: mapper.createObjectNode().also { responses.set<ObjectNode>("200", it) }
            val content = mapper.createObjectNode()
            val text = mapper.createObjectNode()
            text.set<ObjectNode>("schema", mapper.createObjectNode().put("type", "string"))
            content.set<ObjectNode>("text/plain", text)
            response.set<ObjectNode>("content", content)
        }
    }

    private fun validateCoverage(
        document: ObjectNode,
        schemas: ObjectNode,
        projected: Map<String, ObjectNode>,
    ) {
        // Fail closed when a live $ref points outside the serializer catalog.
        val refs = mutableSetOf<String>()
        collectRefs(document.get("paths"), refs)
        val unmapped = refs.filter { it !in DtoSchemaProjector.catalog }.toSet()
        check(unmapped.isEmpty()) { "DTO component lacks serializer mapping: $unmapped" }
        // Prune kapt's orphan JavaBean components (e.g. enum components): DTO schemas
        // inline enums, so those components become unreferenced after replacement.
        val existing = schemas.fieldNames().asSequence().toSet()
        for (name in existing.filter { it !in projected && it !in DtoSchemaProjector.catalog }) {
            schemas.remove(name)
        }
    }

    private fun collectRefs(
        node: com.fasterxml.jackson.databind.JsonNode?,
        out: MutableSet<String>,
    ) {
        if (node == null) {
            return
        }
        if (node.isObject) {
            val ref = node.get("\$ref")
            if (ref != null && ref.isTextual && ref.asText().startsWith("#/components/schemas/")) {
                out.add(ref.asText().substringAfterLast('/'))
            }
            val fields = node.properties()
            for (entry in fields) {
                collectRefs(entry.value, out)
            }
        } else if (node.isArray) {
            for (child in node) {
                collectRefs(child, out)
            }
        }
    }
}
