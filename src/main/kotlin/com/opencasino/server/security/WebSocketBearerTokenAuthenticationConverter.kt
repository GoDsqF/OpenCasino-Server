package com.opencasino.server.security

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Browsers cannot set Authorization on a WebSocket handshake. Phase 5 contract:
 * accept the JWT in any of three places, in this priority:
 *   1. Authorization: Bearer <jwt>       (delegated to Spring default)
 *   2. ?token=<jwt>                       (handy for wscat / mobile clients)
 *   3. Sec-WebSocket-Protocol: bearer,<jwt>  (K8s-style; server echoes `bearer` only)
 */
class WebSocketBearerTokenAuthenticationConverter(
    private val delegate: ServerBearerTokenAuthenticationConverter = ServerBearerTokenAuthenticationConverter(),
) : ServerAuthenticationConverter {

    override fun convert(exchange: ServerWebExchange): Mono<Authentication> =
        delegate.convert(exchange)
            .switchIfEmpty(Mono.defer { Mono.justOrEmpty(tokenFromRequest(exchange)) })

    private fun tokenFromRequest(exchange: ServerWebExchange): Authentication? {
        val token = tokenFromQuery(exchange) ?: tokenFromSubProtocol(exchange) ?: return null
        return BearerTokenAuthenticationToken(token)
    }

    private fun tokenFromQuery(exchange: ServerWebExchange): String? =
        exchange.request.queryParams.getFirst(QUERY_PARAM)?.takeIf { it.isNotBlank() }

    private fun tokenFromSubProtocol(exchange: ServerWebExchange): String? {
        val raw = exchange.request.headers.getFirst(SUB_PROTOCOL_HEADER) ?: return null
        val values = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val bearerIdx = values.indexOf(SUB_PROTOCOL_BEARER)
        if (bearerIdx == -1) return null
        return values.asSequence()
            .filterIndexed { i, _ -> i != bearerIdx }
            .firstOrNull { it.isNotBlank() }
    }

    companion object {
        const val QUERY_PARAM = "token"
        const val SUB_PROTOCOL_HEADER = "Sec-WebSocket-Protocol"
        const val SUB_PROTOCOL_BEARER = "bearer"
    }
}
