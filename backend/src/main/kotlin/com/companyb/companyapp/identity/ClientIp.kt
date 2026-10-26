package com.companyb.companyapp.identity

import io.github.cdimascio.dotenv.dotenv
import io.javalin.http.Context
import java.net.InetAddress

/**
 * Who is calling, for the per-client rate budgets (sign-in, password reset, feedback).
 *
 * Prod runs behind one reverse proxy (Traefik), so the direct peer is the proxy's address for every
 * user and would put all staff in one budget. The proxy puts the real client in `X-Forwarded-For`.
 * That header is honoured only when the direct peer is a trusted proxy, otherwise any caller could
 * pick a fresh address per request and never hit the limit.
 *
 * Trusted peers come from `TRUSTED_PROXIES` (comma-separated addresses or CIDR ranges). The default
 * is loopback plus private ranges: the app's port is published to no host interface, so only
 * containers on the Docker network can be such a peer. Narrow it to the proxy's address to be stricter.
 */
object ClientIp {
    private const val TRUSTED_ENV = "TRUSTED_PROXIES"
    private const val DEFAULT_TRUSTED = "127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7"
    private const val FORWARDED_FOR = "X-Forwarded-For"
    private const val IPV4_BYTES = 4
    private const val IPV4_BITS = 32
    private const val IPV6_BITS = 128
    private const val BITS_PER_BYTE = 8
    private val ADDRESS_LITERAL = Regex("""^([0-9]{1,3}(\.[0-9]{1,3}){3}|[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*)$""")

    internal class Cidr(
        private val network: ByteArray,
        private val prefixBits: Int,
    ) {
        fun contains(address: ByteArray): Boolean {
            val sameFamily = address.size == network.size
            return sameFamily && (0 until prefixBits).all { bitAt(address, it) == bitAt(network, it) }
        }

        private fun bitAt(
            bytes: ByteArray,
            bit: Int,
        ): Int = (bytes[bit / BITS_PER_BYTE].toInt() shr (BITS_PER_BYTE - 1 - bit % BITS_PER_BYTE)) and 1
    }

    private val defaults: List<Cidr> by lazy {
        parseTrusted(dotenv { ignoreIfMissing = true }[TRUSTED_ENV] ?: DEFAULT_TRUSTED)
    }

    @Volatile
    private var override: List<Cidr>? = null

    private val trusted: List<Cidr> get() = override ?: defaults

    /** Entries that are not an address or range are dropped, so a typo narrows trust rather than widening it. */
    internal fun parseTrusted(spec: String): List<Cidr> =
        spec
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                val address = entry.substringBefore('/')
                val bytes = literalBytes(address) ?: return@mapNotNull null
                val maxBits = if (bytes.size == IPV4_BYTES) IPV4_BITS else IPV6_BITS
                val bits = if ('/' in entry) entry.substringAfter('/').toIntOrNull() else maxBits
                if (bits == null || bits !in 0..maxBits) null else Cidr(bytes, bits)
            }

    internal fun resolve(
        peer: String,
        forwardedFor: String?,
        trusted: List<Cidr>,
    ): String {
        if (!isTrusted(peer, trusted)) return peer
        val hops =
            forwardedFor
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        // Walk from the proxy end: the first hop we do not trust is the client. Entries to its left
        // were written by the client itself and prove nothing.
        val client = hops.asReversed().firstOrNull { !isTrusted(it, trusted) }
        return when {
            client != null && literalBytes(client) != null -> client
            else -> peer
        }
    }

    fun of(context: Context): String = resolve(context.ip(), context.header(FORWARDED_FOR), trusted)

    internal fun trustedForTest(trusted: List<Cidr>) {
        override = trusted
    }

    internal fun resetForTest() {
        override = null
    }

    private fun isTrusted(
        address: String,
        trusted: List<Cidr>,
    ): Boolean {
        val bytes = literalBytes(address) ?: return false
        return trusted.any { it.contains(bytes) }
    }

    // The regex guarantees a numeric literal, so getByName never does a DNS lookup.
    private fun literalBytes(address: String): ByteArray? =
        if (ADDRESS_LITERAL.matches(address)) {
            runCatching { InetAddress.getByName(address).address }.getOrNull()
        } else {
            null
        }
}
