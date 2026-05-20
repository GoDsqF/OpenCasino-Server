package com.opencasino.server.security

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class OAuth2LoginFailureHandler(
    private val authProperties: AuthProperties,
    private val auditLogger: SecurityAuditLogger,
    private val clientIpResolver: ClientIpResolver,
) : ServerAuthenticationFailureHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        webFilterExchange: WebFilterExchange,
        exception: AuthenticationException,
    ): Mono<Void> {
        log.warn("OAuth login failed at provider/state stage: {}", exception.message)
        auditLogger.oauthLoginFailure(
            provider = null,
            subject = null,
            code = AuthFailureCode.OAUTH_PROVIDER_ERROR,
            ip = clientIpResolver.resolve(webFilterExchange.exchange),
        )
        val target = authProperties.oauth2.failureRedirect?.takeIf { it.isNotBlank() }
            ?: authProperties.oauth2.successRedirect.takeIf { it.isNotBlank() }
            ?: return Mono.fromRunnable {
                webFilterExchange.exchange.response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
            }
        return OAuth2RedirectWriter.errorRedirect(
            webFilterExchange.exchange,
            target,
            AuthFailureCode.OAUTH_PROVIDER_ERROR,
        )
    }
}
