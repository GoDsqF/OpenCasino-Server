package com.opencasino.server.security

import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import java.math.BigInteger
import java.net.InetAddress

@Component
class ClientIpResolver(props: SecurityNetworkProperties) {

    private val trustedNetworks: List<IpRange> =
        props.trustedProxies.mapNotNull { IpRange.parse(it) }

    fun resolve(exchange: ServerWebExchange): String? {
        val remote = exchange.request.remoteAddress?.address?.hostAddress
        val xff = exchange.request.headers.getFirst(X_FORWARDED_FOR)
        // If the connecting hop is unknown (e.g. in-process test client), treat it
        // as trusted only when we have an XFF to consult — otherwise nothing to report.
        val connectingTrusted = remote == null || trustedNetworks.isNotEmpty() && isTrusted(remote)
        if (!connectingTrusted) return remote
        if (xff == null) return remote
        return xff.splitToSequence(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
            .asReversed()
            .firstOrNull { !isTrusted(it) }
            ?: remote
    }

    private fun isTrusted(ip: String): Boolean =
        trustedNetworks.any { it.contains(ip) }

    companion object {
        const val X_FORWARDED_FOR = "X-Forwarded-For"
    }
}

internal class IpRange private constructor(
    private val network: BigInteger,
    private val mask: BigInteger,
    private val length: Int,
) {
    fun contains(ip: String): Boolean {
        val addr = parseAddress(ip) ?: return false
        if (addr.first != length) return false
        return addr.second.and(mask) == network
    }

    companion object {
        fun parse(raw: String): IpRange? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val slash = trimmed.indexOf('/')
            val (ip, prefixLen) = if (slash < 0) {
                trimmed to -1
            } else {
                trimmed.substring(0, slash) to trimmed.substring(slash + 1).toIntOrNull()
            }
            val parsed = parseAddress(ip) ?: return null
            val (length, value) = parsed
            val effectivePrefix = when {
                prefixLen == null -> return null
                prefixLen < 0 -> length
                prefixLen > length -> return null
                else -> prefixLen
            }
            val mask = if (effectivePrefix == 0) {
                BigInteger.ZERO
            } else {
                BigInteger.ONE.shiftLeft(length).minus(BigInteger.ONE)
                    .shiftRight(length - effectivePrefix)
                    .shiftLeft(length - effectivePrefix)
            }
            return IpRange(value.and(mask), mask, length)
        }

        private fun parseAddress(raw: String): Pair<Int, BigInteger>? {
            return try {
                val ip = raw.substringBefore('%')
                val addr = InetAddress.getByName(ip)
                val bytes = addr.address
                val length = bytes.size * 8
                val signed = ByteArray(bytes.size + 1)
                System.arraycopy(bytes, 0, signed, 1, bytes.size)
                length to BigInteger(signed)
            } catch (_: Exception) {
                null
            }
        }
    }
}