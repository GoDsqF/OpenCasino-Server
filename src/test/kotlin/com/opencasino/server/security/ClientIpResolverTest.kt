package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class ClientIpResolverTest {

    @Test
    fun `returns remoteAddress when trusted proxies are empty`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = emptyList()))
        val exchange = exchange(remoteHost = "1.2.3.4", xff = "9.9.9.9, 8.8.8.8")
        assertEquals("1.2.3.4", resolver.resolve(exchange))
    }

    @Test
    fun `ignores XFF when remoteAddress is not in trusted-proxies`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("10.0.0.0/8")))
        val exchange = exchange(remoteHost = "1.2.3.4", xff = "9.9.9.9")
        assertEquals("1.2.3.4", resolver.resolve(exchange))
    }

    @Test
    fun `picks first untrusted IP from the right of XFF when remoteAddress is trusted`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("10.0.0.0/8")))
        val exchange = exchange(remoteHost = "10.0.0.2", xff = "1.1.1.1, 2.2.2.2, 10.0.0.1")
        assertEquals("2.2.2.2", resolver.resolve(exchange))
    }

    @Test
    fun `falls back to remoteAddress when entire XFF chain is trusted`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("10.0.0.0/8")))
        val exchange = exchange(remoteHost = "10.0.0.2", xff = "10.0.0.5, 10.0.0.1")
        assertEquals("10.0.0.2", resolver.resolve(exchange))
    }

    @Test
    fun `single-IP CIDR (no slash) matches exact address`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("127.0.0.1")))
        val exchange = exchange(remoteHost = "127.0.0.1", xff = "1.1.1.1")
        assertEquals("1.1.1.1", resolver.resolve(exchange))
    }

    @Test
    fun `IPv6 trusted-proxies match IPv6 remote`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("::1/128")))
        val exchange = exchange(remoteHost = "::1", xff = "1.1.1.1")
        assertEquals("1.1.1.1", resolver.resolve(exchange))
    }

    @Test
    fun `returns remoteAddress when XFF header is missing`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("10.0.0.0/8")))
        val exchange = exchange(remoteHost = "10.0.0.2", xff = null)
        assertEquals("10.0.0.2", resolver.resolve(exchange))
    }

    @Test
    fun `null remoteAddress without XFF yields null result`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = emptyList()))
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        assertNull(resolver.resolve(exchange))
    }

    @Test
    fun `null remoteAddress with untrusted XFF returns rightmost untrusted hop`() {
        // In-process test clients (MockServerHttpRequest, @AutoConfigureWebTestClient)
        // surface null remoteAddress. Treat it as a trusted hop and still consult XFF.
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("127.0.0.0/8")))
        val exchange = MockServerHttpRequest.get("/").header(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.7").build()
        assertEquals("203.0.113.7", resolver.resolve(MockServerWebExchange.from(exchange)))
    }

    @Test
    fun `blank entries in trusted-proxies are ignored`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("", "  ", "10.0.0.0/8")))
        val exchange = exchange(remoteHost = "10.0.0.2", xff = "1.1.1.1")
        assertEquals("1.1.1.1", resolver.resolve(exchange))
    }

    @Test
    fun `malformed trusted-proxy entries are ignored without breaking the resolver`() {
        val resolver = ClientIpResolver(SecurityNetworkProperties(trustedProxies = listOf("not-an-ip", "10.0.0.0/8")))
        val exchange = exchange(remoteHost = "10.0.0.2", xff = "1.1.1.1")
        assertEquals("1.1.1.1", resolver.resolve(exchange))
    }

    private fun exchange(remoteHost: String, xff: String?): MockServerWebExchange {
        val builder = MockServerHttpRequest.get("/auth/login")
            .remoteAddress(java.net.InetSocketAddress(java.net.InetAddress.getByName(remoteHost), 12345))
        if (xff != null) builder.header(ClientIpResolver.X_FORWARDED_FOR, xff)
        return MockServerWebExchange.from(builder.build())
    }
}