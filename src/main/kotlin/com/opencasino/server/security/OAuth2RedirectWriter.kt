package com.opencasino.server.security

import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.net.URI

internal object OAuth2RedirectWriter {

    fun successRedirect(
        exchange: ServerWebExchange,
        base: String,
        issued: IssuedToken,
        refresh: IssuedRefresh,
    ): Mono<Void> =
        sendRedirect(
            exchange,
            buildUri(
                base,
                mapOf(
                    "token" to issued.token,
                    "expiresAt" to issued.expiresAt.toString(),
                    "refresh" to refresh.plaintext,
                    "refreshExpiresAt" to refresh.expiresAt.toString(),
                ),
            ),
        )

    fun errorRedirect(exchange: ServerWebExchange, base: String, code: AuthFailureCode): Mono<Void> =
        sendRedirect(exchange, buildUri(base, mapOf("error" to code.name)))

    private fun buildUri(base: String, params: Map<String, String>): URI {
        val builder = UriComponentsBuilder.fromUriString(base)
        params.forEach { (k, v) -> builder.queryParam(k, v) }
        return builder.encode().build().toUri()
    }

    private fun sendRedirect(exchange: ServerWebExchange, uri: URI): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.FOUND
        response.headers.location = uri
        return response.setComplete()
    }
}
