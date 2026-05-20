package com.opencasino.server.security

import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange

data class ClientContext(
    val userAgent: String?,
    val ip: String?,
) {
    companion object {
        val EMPTY = ClientContext(userAgent = null, ip = null)

        fun from(exchange: ServerWebExchange, ipResolver: ClientIpResolver): ClientContext =
            ClientContext(
                userAgent = exchange.request.headers.getFirst(HttpHeaders.USER_AGENT),
                ip = ipResolver.resolve(exchange),
            )
    }
}