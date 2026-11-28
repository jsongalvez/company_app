package com.companyb.companyapp.branchday

import org.junit.Assume.assumeTrue
import java.net.http.HttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the server's ACME v2 DNS-01 client: key encoding, PKCS#10 CSR generation, and
 * (when `PEBBLE_DIRECTORY` is set, as in CI) full issuance against Let's Encrypt's Pebble server.
 */
class AcmeClientTest {
    @Test
    fun `EC keypair encode-decode round-trips identically`() {
        val pair = AcmeClient.generateEcKeyPair()
        val decoded = AcmeClient.decodeKeyPair(AcmeClient.encodeKeyPair(pair))
        assertContentEquals(pair.public.encoded, decoded.public.encoded)
        assertContentEquals(pair.private.encoded, decoded.private.encoded)
    }

    @Test
    fun `PKCS10 CSR is DER-encoded SEQUENCE containing domain`() {
        val pair = AcmeClient.generateEcKeyPair()
        val csr = AcmeClient.buildCsr("hub.kiel.party", pair)
        assertEquals(0x30.toByte(), csr[0])
        assertTrue(csr.toString(Charsets.US_ASCII).contains("hub.kiel.party"))
    }

    @Test
    fun `obtains a real hub certificate from Pebble via DNS-01`() {
        val dir = System.getenv("PEBBLE_DIRECTORY")?.takeIf { it.isNotBlank() }
        assumeTrue("PEBBLE_DIRECTORY not set", dir != null)
        val txt = RecordingTxt()
        val client =
            AcmeClient(
                directoryUrl = dir!!,
                http = insecureHttpClientForPebble(),
                pollInterval = Duration.ofMillis(250),
            )
        val accountKey = AcmeClient.generateEcKeyPair()
        val issued = client.obtain("hub.kiel.party", accountKey, txt, Duration.ZERO)
        assertTrue(issued.certPem.contains("BEGIN CERTIFICATE"))
        assertTrue(issued.keyPem.contains("BEGIN PRIVATE KEY"))
        assertTrue(issued.notAfter.isAfter(issued.notBefore))
        assertEquals(1, txt.puts.size)
        assertEquals(1, txt.removes.size)
        assertEquals(txt.puts.single(), txt.removes.single())
        assertEquals("_acme-challenge.hub.kiel.party", txt.puts.single().first)
        val challengeValue = txt.puts.single().second
        assertEquals(43, challengeValue.length)
    }

    private class RecordingTxt : SiteHubCerts.Txt {
        val puts = mutableListOf<Pair<String, String>>()
        val removes = mutableListOf<Pair<String, String>>()

        override fun put(
            name: String,
            value: String,
        ) {
            puts += name to value
        }

        override fun remove(
            name: String,
            value: String,
        ) {
            removes += name to value
        }
    }

    private fun insecureHttpClientForPebble(): HttpClient {
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        return HttpClient.newBuilder().sslContext(ssl).build()
    }
}
