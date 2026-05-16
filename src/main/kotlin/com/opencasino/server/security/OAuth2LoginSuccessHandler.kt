package com.opencasino.server.security

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class OAuth2LoginSuccessHandler(
    private val linkingService: OAuth2UserLinkingService,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenService: RefreshTokenService,
    private val authProperties: AuthProperties,
) : ServerAuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationSuccess(
        webFilterExchange: WebFilterExchange,
        authentication: Authentication,
    ): Mono<Void> {
        val exchange = webFilterExchange.exchange
        val token = authentication as? OAuth2AuthenticationToken
            ?: return errorRedirect(exchange, AuthFailureCode.OAUTH_PROVIDER_ERROR)
        val principal = principalFrom(token)
            ?: return errorRedirect(exchange, AuthFailureCode.OAUTH_PROVIDER_ERROR)

        return linkingService.linkOrCreate(principal)
            .flatMap { user -> successRedirect(exchange, user) }
            .onErrorResume(AuthException::class.java) { ex ->
                log.warn("OAuth login rejected for {}/{}: {}", principal.provider, principal.subject, ex.failure)
                errorRedirect(exchange, ex.failure)
            }
            .onErrorResume(Throwable::class.java) { ex ->
                log.error("OAuth login failed unexpectedly for {}/{}", principal.provider, principal.subject, ex)
                errorRedirect(exchange, AuthFailureCode.OAUTH_PROVIDER_ERROR)
            }
    }

    private fun principalFrom(token: OAuth2AuthenticationToken): OAuth2UserPrincipal? {
        val provider = token.authorizedClientRegistrationId
        val user = token.principal
        // Phase 6 = Google only, which is OIDC and always yields an OidcUser.
        // Non-OIDC providers (future Yandex/GitHub) need their own mapping.
        if (user !is OidcUser) return null
        return OAuth2UserPrincipal(
            provider = provider,
            subject = user.subject,
            email = user.email,
            emailVerified = user.emailVerified ?: false,
            profileName = user.fullName ?: user.preferredUsername ?: user.givenName,
        )
    }

    private fun successRedirect(exchange: org.springframework.web.server.ServerWebExchange, user: com.opencasino.server.user.User): Mono<Void> {
        val target = authProperties.oauth2.successRedirect
        if (target.isBlank()) {
            log.error("app.auth.oauth2.success-redirect is unset; cannot complete OAuth login")
            return errorRedirect(exchange, AuthFailureCode.OAUTH_PROVIDER_ERROR)
        }
        val issued = jwtIssuer.issueAccess(user)
        return refreshTokenService.issue(user.id)
            .flatMap { refresh -> OAuth2RedirectWriter.successRedirect(exchange, target, issued, refresh) }
    }

    private fun errorRedirect(exchange: org.springframework.web.server.ServerWebExchange, code: AuthFailureCode): Mono<Void> {
        val target = authProperties.oauth2.failureRedirect?.takeIf { it.isNotBlank() }
            ?: authProperties.oauth2.successRedirect.takeIf { it.isNotBlank() }
            ?: return Mono.fromRunnable {
                exchange.response.statusCode = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
            }
        return OAuth2RedirectWriter.errorRedirect(exchange, target, code)
    }
}
