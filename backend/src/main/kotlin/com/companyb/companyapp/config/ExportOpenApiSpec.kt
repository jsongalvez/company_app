package com.companyb.companyapp.config

import java.nio.file.Files
import java.nio.file.Paths

/**
 * #495 — Gradle-owned offline export. Reads the same kapt-generated classpath
 * resource the production OpenApiPlugin serves, applies the canonical
 * transformation, and writes the final document. Inputs are compiled
 * classes/resources plus serializer metadata, never file modification times.
 */
fun main(args: Array<String>) {
    val output = args.firstOrNull() ?: "backend/build/openapi/openapi-canonical.json"
    val canonical = OpenApiCanonical.canonicalize(OpenApiCanonical.kaptResourceText())
    val path = Paths.get(output)
    Files.createDirectories(path.parent)
    Files.writeString(path, canonical + "\n")
    println("Exported canonical OpenAPI to $output")
}
