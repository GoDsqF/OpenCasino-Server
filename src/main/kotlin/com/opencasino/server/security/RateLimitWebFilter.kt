package com.opencasino.server.security

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
class RateLimitWebFilter(
    private val props: RateLimitProperties,
    private val ipResolver: ClientIpResolver,
    private val auditLogger: SecurityAuditLogger,
) : WebFilter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!props.enabled) return chain.filter(exchange)

        val route = classify(exchange.request) ?: return chain.filter(exchange)
        val identity = identityFor(exchange, route)
        val bucket = bucketFor(route, identity)
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        return if (probe.isConsumed) {
            chain.filter(exchange)
        } else {
            val retryAfterSec = (probe.nanosToWaitForRefill / 1_000_000_000L).coerceAtLeast(1L)
            val ip = ipResolver.resolve(exchange)
            auditLogger.rateLimitExceeded(route.name, ip)
            log.debug("Rate-limit exceeded for route={} identity={} retryAfter={}s", route.name, identity, retryAfterSec)
            with(exchange.response) {
                statusCode = HttpStatus.TOO_MANY_REQUESTS
                headers[HttpHeaders.RETRY_AFTER] = retryAfterSec.toString()
                setComplete()
            }
        }
    }

    private fun classify(request: ServerHttpRequest): Route? {
        val path = request.path.value()
        val method = request.method
        return when {
            method == HttpMethod.POST && path == "/auth/login" -> Route.LOGIN
            method == HttpMethod.POST && path == "/auth/register" -> Route.REGISTER
            method == HttpMethod.POST && path == "/auth/refresh" -> Route.REFRESH
            method == HttpMethod.GET && path.startsWith("/ws/") -> Route.WS_HANDSHAKE
            method == HttpMethod.POST && path == "/auth/logout" -> Route.AUTHENTICATED
            method == HttpMethod.GET && path == "/auth/me" -> Route.AUTHENTICATED
            else -> null
        }
    }

    private fun identityFor(exchange: ServerWebExchange, route: Route): String {
        if (route == Route.AUTHENTICATED) {
            val token = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.removePrefix("Bearer ")?.trim()
            if (!token.isNullOrEmpty()) return "jwt:${jwtSubjectHint(token)}"
        }
        return "ip:" + (ipResolver.resolve(exchange) ?: "unknown")
    }

    // Cheap signature-free hint at the token's "sub" claim. Used purely as a bucket key:
    // an attacker could swap tokens to dodge it, but they'd still hit the per-IP buckets
    // upstream. The bucket map does not need cryptographic isolation.
    private fun jwtSubjectHint(token: String): String {
        val parts = token.split('.')
        if (parts.size < 2) return token.hashCode().toString()
        return parts[1].hashCode().toString()
    }

    private fun bucketFor(route: Route, identity: String): Bucket {
        val key = "${route.name}|$identity"
        return buckets.computeIfAbsent(key) { newBucket(route.spec(props)) }
    }

    private fun newBucket(spec: RateLimitProperties.BucketSpec): Bucket {
        val limit = Bandwidth.classic(spec.capacity, Refill.intervally(spec.refill, spec.period))
        return Bucket.builder().addLimit(limit).build()
    }

    enum class Route(val keyByIp: Boolean) {
        LOGIN(true) {
            override fun spec(p: RateLimitProperties) = p.login
        },
        REGISTER(true) {
            override fun spec(p: RateLimitProperties) = p.register
        },
        REFRESH(true) {
            override fun spec(p: RateLimitProperties) = p.refresh
        },
        WS_HANDSHAKE(true) {
            override fun spec(p: RateLimitProperties) = p.wsHandshake
        },
        AUTHENTICATED(false) {
            override fun spec(p: RateLimitProperties) = p.authenticated
        };

        abstract fun spec(p: RateLimitProperties): RateLimitProperties.BucketSpec
    }
}
