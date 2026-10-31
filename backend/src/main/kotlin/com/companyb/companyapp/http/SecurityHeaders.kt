package com.companyb.companyapp.http

import com.companyb.companyapp.observability.ErrorTrackerConfig
import io.javalin.http.Context
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

/**
 * Headers that limit what an injected script could do, on every response. The page's inline scripts change with
 * each build (one lists the build's file names), so their hashes are read from the served index.html, not written here.
 */
class SecurityHeaders(
    private val webDir: String?,
    errorTracker: ErrorTrackerConfig?,
) {
    private val connectSrc =
        listOfNotNull("'self'", errorTracker?.let { URI(it.envelopeUrl).let { u -> "${u.scheme}://${u.authority}" } })
            .joinToString(" ")

    private var cachedFor: Long = -1
    private var cachedPolicy: String = policy(emptyList())

    fun apply(context: Context) {
        context.header("Content-Security-Policy", currentPolicy())
        context.header("Strict-Transport-Security", "max-age=31536000")
        context.header("X-Content-Type-Options", "nosniff")
        context.header("X-Frame-Options", "DENY")
        context.header("Referrer-Policy", "strict-origin-when-cross-origin")
    }

    @Synchronized
    private fun currentPolicy(): String {
        val index = webDir?.let { File(it, "index.html") }?.takeIf { it.isFile } ?: return cachedPolicy
        val stamp = index.lastModified()
        if (stamp != cachedFor) {
            cachedPolicy = policy(inlineScriptHashes(index.readText()))
            cachedFor = stamp
        }
        return cachedPolicy
    }

    private fun policy(scriptHashes: List<String>): String =
        listOf(
            "default-src 'self'",
            "script-src 'self' ${scriptHashes.joinToString(" ")}".trimEnd(),
            // Svelte sets style attributes, and the saved-screen script writes the last screen's styles inline.
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "connect-src $connectSrc",
            "worker-src 'self'",
            "manifest-src 'self'",
            "object-src 'none'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'",
        ).joinToString("; ")

    companion object {
        private val INLINE_SCRIPT = Regex("<script(\\s[^>]*)?>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)

        fun inlineScriptHashes(html: String): List<String> =
            INLINE_SCRIPT
                .findAll(html)
                .filter { m -> !m.groupValues[1].contains(Regex("\\ssrc\\s*=")) && m.groupValues[2].isNotEmpty() }
                .map { m ->
                    val digest = MessageDigest.getInstance("SHA-256").digest(m.groupValues[2].toByteArray())
                    "'sha256-${Base64.getEncoder().encodeToString(digest)}'"
                }.distinct()
                .toList()
    }
}
