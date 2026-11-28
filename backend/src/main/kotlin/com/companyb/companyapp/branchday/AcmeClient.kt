package com.companyb.companyapp.branchday

import com.companyb.companyapp.observability.traced
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Minimal RFC 8555 ACME v2 client for DNS-01 issuance of the shared `hub.kiel.party` certificate.
 * Uses JDK 21's built-in ECDSA (ES256 / P-256), X.509 parser and HttpClient with no extra runtime deps.
 */
class AcmeClient(
    private val directoryUrl: String = LETS_ENCRYPT_DIRECTORY,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build(),
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) : SiteHubCerts.CertIssuer {
    override fun obtain(
        domain: String,
        accountKey: KeyPair,
        txt: SiteHubCerts.Txt,
        propagationWait: Duration,
    ): SiteHubCerts.IssuedCert {
        val dir = fetchDirectory()
        val session = Session(dir, accountKey)
        session.registerAccount()
        val order = session.newOrder(domain)
        for (authzUrl in order.authzUrls) {
            proveDns01(session, domain, authzUrl, txt, propagationWait)
        }
        val leafKey = generateEcKeyPair()
        val csrDer = buildCsr(domain, leafKey)
        session.finalizeOrder(order.finalizeUrl, csrDer)
        val certUrl = waitOrderCertificate(session, order.orderUrl)
        val certPem = session.downloadCertificate(certUrl)
        return parseIssuedCert(domain, certPem, leafKey)
    }

    private fun fetchDirectory(): Directory {
        val req =
            HttpRequest
                .newBuilder(URI.create(directoryUrl))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build()
        val res = sendHttp(req)
        if (res.statusCode() / HUNDRED != 2) {
            throw IOException("ACME directory returned ${res.statusCode()}")
        }
        val obj = Json.parseToJsonElement(res.body()).jsonObject
        return Directory(
            newNonce = obj.requireString("newNonce"),
            newAccount = obj.requireString("newAccount"),
            newOrder = obj.requireString("newOrder"),
        )
    }

    private fun proveDns01(
        session: Session,
        domain: String,
        authzUrl: String,
        txt: SiteHubCerts.Txt,
        propagationWait: Duration,
    ) {
        val authz = session.postAsGetJson(authzUrl)
        if (authz["status"]?.jsonPrimitive?.content == "valid") return
        val challenge =
            authz["challenges"]
                ?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["type"]?.jsonPrimitive?.content == "dns-01" }
                ?: throw IOException("ACME offered no dns-01 challenge for $domain")
        val challengeUrl = challenge.requireString("url")
        val token = challenge.requireString("token")
        val recordName = "_acme-challenge.$domain"
        val txtValue = dns01Value(token, session.thumbprint)
        txt.put(recordName, txtValue)
        try {
            pause(propagationWait)
            session.signedJson(challengeUrl, "{}")
            waitAuthzValid(session, authzUrl)
        } finally {
            try {
                txt.remove(recordName, txtValue)
            } catch (e: IOException) {
                logger.warn { "[SITE-HUB] DNS cleanup failed for $recordName: ${e.message}" }
            }
        }
    }

    private fun waitAuthzValid(
        session: Session,
        authzUrl: String,
    ) {
        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            val status = session.postAsGetJson(authzUrl)["status"]?.jsonPrimitive?.content
            when (status) {
                "valid" -> return
                "invalid", "deactivated", "expired", "revoked" -> throw IOException("ACME authorization $status")
            }
            if (attempt < MAX_POLL_ATTEMPTS) pause(pollInterval)
        }
        throw IOException("ACME authorization timed out")
    }

    private fun waitOrderCertificate(
        session: Session,
        orderUrl: String,
    ): String {
        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            val obj = session.postAsGetJson(orderUrl)
            val status = obj["status"]?.jsonPrimitive?.content
            val certUrl = obj["certificate"]?.jsonPrimitive?.content
            when {
                status == "valid" && !certUrl.isNullOrBlank() -> return certUrl
                status == "invalid" -> throw IOException("ACME order invalid: $obj")
            }
            if (attempt < MAX_POLL_ATTEMPTS) pause(pollInterval)
        }
        throw IOException("ACME order timed out waiting for certificate")
    }

    private fun sendHttp(req: HttpRequest): HttpResponse<String> =
        traced("AcmeClient.sendHttp") {
            try {
                http.send(req, HttpResponse.BodyHandlers.ofString())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("ACME request interrupted", e)
            }
        }

    private fun pause(duration: Duration) {
        val millis = duration.toMillis()
        if (millis <= 0L) return
        try {
            CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("ACME wait interrupted", e)
        }
    }

    private class Directory(
        val newNonce: String,
        val newAccount: String,
        val newOrder: String,
    )

    private class Order(
        val orderUrl: String,
        val finalizeUrl: String,
        val authzUrls: List<String>,
    )

    private inner class Session(
        private val dir: Directory,
        private val accountKey: KeyPair,
    ) {
        private val jwkJson: String = canonicalEcJwk(accountKey.public)
        val thumbprint: String = b64u(sha256(jwkJson.toByteArray()))
        private var nonce: String? = null
        private var kid: String? = null

        fun registerAccount() {
            val res = signedPost(dir.newAccount, """{"termsOfServiceAgreed":true}""", useJwk = true)
            if (res.statusCode() / HUNDRED != 2) {
                throw IOException("ACME account registration failed (${res.statusCode()}): ${res.body()}")
            }
            kid =
                res.headers().firstValue("Location").orElse(null)
                    ?: throw IOException("ACME newAccount answer had no Location header")
        }

        fun newOrder(domain: String): Order {
            val res = signedPost(dir.newOrder, """{"identifiers":[{"type":"dns","value":"$domain"}]}""")
            if (res.statusCode() / HUNDRED != 2) {
                throw IOException("ACME newOrder failed (${res.statusCode()}): ${res.body()}")
            }
            val orderUrl =
                res.headers().firstValue("Location").orElse(null)
                    ?: throw IOException("ACME newOrder answer had no Location header")
            val obj = Json.parseToJsonElement(res.body()).jsonObject
            val authzUrls = obj["authorizations"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            return Order(orderUrl, obj.requireString("finalize"), authzUrls)
        }

        fun finalizeOrder(
            finalizeUrl: String,
            csrDer: ByteArray,
        ) {
            val res = signedPost(finalizeUrl, """{"csr":"${b64u(csrDer)}"}""")
            if (res.statusCode() / HUNDRED != 2) {
                throw IOException("ACME finalize failed (${res.statusCode()}): ${res.body()}")
            }
        }

        fun postAsGetJson(url: String): JsonObject {
            val res = signedPost(url, null)
            if (res.statusCode() / HUNDRED != 2) {
                throw IOException("ACME POST-as-GET failed (${res.statusCode()}): ${res.body()}")
            }
            return Json.parseToJsonElement(res.body()).jsonObject
        }

        fun signedJson(
            url: String,
            payload: String,
        ): JsonObject {
            val res = signedPost(url, payload)
            if (res.statusCode() / HUNDRED != 2) {
                throw IOException("ACME POST failed (${res.statusCode()}): ${res.body()}")
            }
            return Json.parseToJsonElement(res.body()).jsonObject
        }

        fun downloadCertificate(certUrl: String): String {
            val res = signedPost(certUrl, null)
            if (res.statusCode() / HUNDRED != 2) {
                throw IOException("ACME certificate download failed (${res.statusCode()}): ${res.body()}")
            }
            return res.body()
        }

        private fun signedPost(
            url: String,
            payload: String?,
            useJwk: Boolean = false,
        ): HttpResponse<String> {
            var last: HttpResponse<String>? = null
            for (attempt in 1..MAX_NONCE_RETRIES) {
                val activeNonce = nonce ?: fetchNonce()
                nonce = null
                val body = buildJws(url, activeNonce, payload, useJwk)
                val req =
                    HttpRequest
                        .newBuilder(URI.create(url))
                        .timeout(HTTP_TIMEOUT)
                        .header("Content-Type", "application/jose+json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()
                val res = sendHttp(req)
                res.headers().firstValue("Replay-Nonce").ifPresent { nonce = it }
                last = res
                if (!isBadNonce(res)) return res
            }
            return last ?: throw IOException("ACME request produced no response")
        }

        private fun fetchNonce(): String {
            val req =
                HttpRequest
                    .newBuilder(URI.create(dir.newNonce))
                    .timeout(HTTP_TIMEOUT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build()
            val res = sendHttp(req)
            return res.headers().firstValue("Replay-Nonce").orElse(null)
                ?: throw IOException("ACME newNonce returned no Replay-Nonce (${res.statusCode()})")
        }

        private fun buildJws(
            url: String,
            activeNonce: String,
            payload: String?,
            useJwk: Boolean,
        ): String {
            val protectedJson =
                if (useJwk) {
                    """{"alg":"ES256","jwk":$jwkJson,"nonce":"$activeNonce","url":"$url"}"""
                } else {
                    val accountUrl = kid ?: throw IOException("ACME account not registered")
                    """{"alg":"ES256","kid":"$accountUrl","nonce":"$activeNonce","url":"$url"}"""
                }
            val protectedB64 = b64u(protectedJson.toByteArray())
            val payloadB64 = if (payload == null) "" else b64u(payload.toByteArray())
            val signingInput = "$protectedB64.$payloadB64".toByteArray(Charsets.US_ASCII)
            val sig =
                Signature
                    .getInstance("SHA256withECDSAinP1363Format")
                    .apply {
                        initSign(accountKey.private)
                        update(signingInput)
                    }.sign()
            return """{"protected":"$protectedB64","payload":"$payloadB64","signature":"${b64u(sig)}"}"""
        }

        private fun isBadNonce(res: HttpResponse<String>): Boolean =
            res.statusCode() == HTTP_BAD_REQUEST && res.body().contains(":badNonce")
    }

    companion object {
        const val LETS_ENCRYPT_DIRECTORY = "https://acme-v02.api.letsencrypt.org/directory"
        private const val HUNDRED = 100
        private const val HTTP_BAD_REQUEST = 400
        private const val MAX_NONCE_RETRIES = 4
        private const val MAX_POLL_ATTEMPTS = 30
        private const val COORD_BYTES = 32
        private const val PEM_LINE_LEN = 64
        private const val INT_BYTES = 4
        private const val SAN_DNS_TYPE = 2
        private const val DER_SHORT_LIMIT = 128
        private const val DER_ONE_BYTE_LIMIT = 256
        private const val BYTE_SHIFT = 8
        private const val DER_LEN_1 = 0x81
        private const val DER_LEN_2 = 0x82
        private const val TAG_INTEGER = 0x02
        private const val TAG_BIT_STRING = 0x03
        private const val TAG_OCTET_STRING = 0x04
        private const val TAG_UTF8_STRING = 0x0C
        private const val TAG_SEQUENCE = 0x30
        private const val TAG_SET = 0x31
        private const val TAG_CTX_DNS = 0x82
        private const val TAG_CTX_ATTRS = 0xA0

        private val OID_CN = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
        private val OID_SAN = byteArrayOf(0x06, 0x03, 0x55, 0x1D, 0x11)
        private val OID_EXT_REQ =
            byteArrayOf(
                0x06,
                0x09,
                0x2A,
                0x86.toByte(),
                0x48,
                0x86.toByte(),
                0xF7.toByte(),
                0x0D,
                0x01,
                0x09,
                0x0E,
            )
        private val OID_ECDSA_SHA256 =
            byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02)

        private val HTTP_TIMEOUT: Duration = Duration.ofSeconds(30)
        private val DEFAULT_POLL_INTERVAL: Duration = Duration.ofSeconds(2)

        fun generateEcKeyPair(): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        fun encodeKeyPair(pair: KeyPair): ByteArray {
            val pub = pair.public.encoded
            val priv = pair.private.encoded
            return ByteBuffer
                .allocate(INT_BYTES + pub.size + priv.size)
                .putInt(pub.size)
                .put(pub)
                .put(priv)
                .array()
        }

        fun decodeKeyPair(bytes: ByteArray): KeyPair {
            val buf = ByteBuffer.wrap(bytes)
            val pubLen = buf.int
            val pub = ByteArray(pubLen).also { buf.get(it) }
            val priv = ByteArray(buf.remaining()).also { buf.get(it) }
            val factory = KeyFactory.getInstance("EC")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(pub)),
                factory.generatePrivate(PKCS8EncodedKeySpec(priv)),
            )
        }

        internal fun parseIssuedCert(
            domain: String,
            certPem: String,
            leafKey: KeyPair,
        ): SiteHubCerts.IssuedCert {
            val factory = CertificateFactory.getInstance("X.509")
            val parsed = factory.generateCertificate(certPem.byteInputStream())
            val leaf = parsed as? X509Certificate ?: throw IOException("ACME returned non-X509 certificate")
            val dnsNames =
                leaf.subjectAlternativeNames
                    ?.filter { it.size >= 2 && it[0] == SAN_DNS_TYPE }
                    ?.mapNotNull { it[1] as? String }
                    .orEmpty()
            if (domain !in dnsNames) {
                throw IOException("Issued certificate SANs $dnsNames do not include $domain")
            }
            val keyB64 = Base64.getMimeEncoder(PEM_LINE_LEN, "\n".toByteArray()).encodeToString(leafKey.private.encoded)
            val keyPem = "-----BEGIN PRIVATE KEY-----\n$keyB64\n-----END PRIVATE KEY-----\n"
            return SiteHubCerts.IssuedCert(
                certPem = certPem,
                keyPem = keyPem,
                notBefore = leaf.notBefore.toInstant(),
                notAfter = leaf.notAfter.toInstant(),
            )
        }

        internal fun buildCsr(
            domain: String,
            pair: KeyPair,
        ): ByteArray {
            val version = tlv(TAG_INTEGER, byteArrayOf(0))
            val cn = tlv(TAG_SEQUENCE, OID_CN + tlv(TAG_UTF8_STRING, domain.toByteArray()))
            val subject = tlv(TAG_SEQUENCE, tlv(TAG_SET, cn))
            val sanValue = tlv(TAG_SEQUENCE, tlv(TAG_CTX_DNS, domain.toByteArray(Charsets.US_ASCII)))
            val sanExt = tlv(TAG_SEQUENCE, OID_SAN + tlv(TAG_OCTET_STRING, sanValue))
            val extReq = tlv(TAG_SEQUENCE, OID_EXT_REQ + tlv(TAG_SET, tlv(TAG_SEQUENCE, sanExt)))
            val cri = tlv(TAG_SEQUENCE, version + subject + pair.public.encoded + tlv(TAG_CTX_ATTRS, extReq))
            val sig =
                Signature
                    .getInstance("SHA256withECDSA")
                    .apply {
                        initSign(pair.private)
                        update(cri)
                    }.sign()
            val algId = tlv(TAG_SEQUENCE, OID_ECDSA_SHA256)
            val bitStr = tlv(TAG_BIT_STRING, byteArrayOf(0) + sig)
            return tlv(TAG_SEQUENCE, cri + algId + bitStr)
        }

        private fun canonicalEcJwk(pub: java.security.PublicKey): String {
            val ec = pub as? ECPublicKey ?: throw IOException("Expected EC public key")
            val x = b64u(unsignedFixedBytes(ec.w.affineX, COORD_BYTES))
            val y = b64u(unsignedFixedBytes(ec.w.affineY, COORD_BYTES))
            return """{"crv":"P-256","kty":"EC","x":"$x","y":"$y"}"""
        }

        private fun unsignedFixedBytes(
            value: BigInteger,
            size: Int,
        ): ByteArray {
            val raw = value.toByteArray()
            val out = ByteArray(size)
            val srcPos = if (raw.size > size) raw.size - size else 0
            val copyLen = minOf(raw.size, size)
            System.arraycopy(raw, srcPos, out, size - copyLen, copyLen)
            return out
        }

        private fun dns01Value(
            token: String,
            thumbprint: String,
        ): String = b64u(sha256("$token.$thumbprint".toByteArray()))

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun b64u(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun tlv(
            tag: Int,
            content: ByteArray,
        ): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            when {
                content.size < DER_SHORT_LIMIT -> {
                    out.write(content.size)
                }

                content.size < DER_ONE_BYTE_LIMIT -> {
                    out.write(DER_LEN_1)
                    out.write(content.size)
                }

                else -> {
                    out.write(DER_LEN_2)
                    out.write(content.size shr BYTE_SHIFT)
                    out.write(content.size)
                }
            }
            out.write(content)
            return out.toByteArray()
        }

        private fun JsonObject.requireString(key: String): String =
            this[key]?.jsonPrimitive?.content ?: throw IOException("ACME JSON missing '$key'")
    }
}
