package com.opencasino.server.security

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
@Profile("!test")
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        jwtDecoder: ReactiveJwtDecoder,
        jwtAuthenticationConverter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
        clientRegistrations: ObjectProvider<ReactiveClientRegistrationRepository>,
        oauth2SuccessHandler: OAuth2LoginSuccessHandler,
        oauth2FailureHandler: OAuth2LoginFailureHandler,
    ): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange {
                it
                    .pathMatchers(
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/static/**",
                        "/auth/register",
                        "/auth/login",
                        "/oauth2/authorization/*",
                        "/login/oauth2/code/*",
                    ).permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { rs ->
                rs
                    .bearerTokenConverter(WebSocketBearerTokenAuthenticationConverter())
                    .jwt { jwt ->
                        jwt
                            .jwtDecoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter)
                    }
            }

        // Wire OAuth login only when at least one client registration is configured.
        // Spring Boot only creates the bean when spring.security.oauth2.client.registration.*
        // properties are present, so empty env vars => the app starts without OAuth.
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login {
                it.authenticationSuccessHandler(oauth2SuccessHandler)
                it.authenticationFailureHandler(oauth2FailureHandler)
            }
        }

        return http.build()
    }
}