package com.companyb.companyapp.branchday

import com.companyb.companyapp.observability.traced
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * TXT records in the kiel.party zone through Cloudflare's API, with a token that may edit that
 * zone's DNS only (`CLOUDFLARE_DNS_TOKEN`, never in git).
 */
class CloudflareTxt(
    private val token: String,
    private val zoneName: String = ZONE,
    private val api: String = "https://api.cloudflare.com/client/v4",
) : SiteHubCerts.Txt {
    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    @Volatile
    private var zoneId: String? = null

    override fun put(
        name: String,
        value: String,
    ) {
        traced("CloudflareTxt.put") {
            val body = """{"type":"TXT","name":"$name","content":"\"$value\"","ttl":60}"""
            call("POST", "/zones/${zone()}/dns_records", body)
        }
    }

    override fun remove(
        name: String,
        value: String,
    ) {
        traced("CloudflareTxt.remove") {
            val q = "type=TXT&name=${URLEncoder.encode(name, Charsets.UTF_8)}"
            val records = "/zones/${zone()}/dns_records"
            val found = call("GET", "$records?$q", null)["result"]?.jsonArray ?: JsonArray(emptyList())
            for (rec in found) {
                val o = rec.jsonObject
                val content = o["content"]?.jsonPrimitive?.content?.trim('"')
                val id = o["id"]?.jsonPrimitive?.content
                if (content == value && id != null) call("DELETE", "$records/$id", null)
            }
        }
    }

    private fun zone(): String =
        zoneId ?: traced("CloudflareTxt.zone") {
            val found = call("GET", "/zones?name=$zoneName", null)["result"]?.jsonArray
            val first = found?.firstOrNull()?.jsonObject
            val id = first?.get("id")?.jsonPrimitive?.content
            (id ?: throw IOException("Cloudflare token can't see the $zoneName zone")).also { zoneId = it }
        }

    private fun call(
        method: String,
        path: String,
        body: String?,
    ): JsonObject =
        traced("CloudflareTxt.call") {
            val req =
                HttpRequest
                    .newBuilder(URI.create(api + path))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .method(method, publisher(body))
                    .build()
            val res = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() / HUNDRED != 2) throw IOException("Cloudflare answered ${res.statusCode()} to $method")
            Json.parseToJsonElement(res.body()).jsonObject
        }

    private fun publisher(body: String?): HttpRequest.BodyPublisher =
        if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)

    companion object {
        const val ZONE = "kiel.party"
        private const val HUNDRED = 100
        private val TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}
