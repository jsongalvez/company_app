package com.companyb.companyapp.branchday

import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the hub certificate's only paid-for dependency, the real Cloudflare zone: the record
 * the server writes is the one Let's Encrypt will read, and removing it leaves nothing behind.
 * Runs only where `CLOUDFLARE_DNS_TOKEN` is set (not in CI); it writes and deletes one test name.
 */
class CloudflareTxtLiveTest {
    private val token = System.getenv("CLOUDFLARE_DNS_TOKEN").orEmpty()
    private val name = "_sitehub-test.kiel.party"

    private fun seen(value: String): Boolean {
        val req =
            HttpRequest
                .newBuilder(URI.create("https://api.cloudflare.com/client/v4/zones?name=kiel.party"))
                .header("Authorization", "Bearer $token")
                .build()
        val http = HttpClient.newHttpClient()
        val zones = http.send(req, HttpResponse.BodyHandlers.ofString()).body()
        val zone = ZONE_ID.find(zones)!!.groupValues[1]
        val list =
            HttpRequest
                .newBuilder(
                    URI.create("https://api.cloudflare.com/client/v4/zones/$zone/dns_records?type=TXT&name=$name"),
                ).header("Authorization", "Bearer $token")
                .build()
        return http.send(list, HttpResponse.BodyHandlers.ofString()).body().contains(value)
    }

    @Test
    fun `writes and removes a TXT record in the real zone`() {
        assumeTrue("CLOUDFLARE_DNS_TOKEN not set", token.isNotBlank())
        val dns = CloudflareTxt(token)
        val value = UUID.randomUUID().toString().replace("-", "")
        dns.put(name, value)
        assertTrue(seen(value), "record not written")
        dns.remove(name, value)
        assertFalse(seen(value), "record left behind")
    }

    private companion object {
        val ZONE_ID = Regex("\"id\":\"([0-9a-f]{32})\"")
    }
}
