package com.opencasino.server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter
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
    ): SecurityWebFilterChain =
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
                    ).permitAll()
                    // TODO(Auth phase 6+): `/oauth2/authorize/{provider}`,
                    // `/oauth2/redirect/{provider}`, `/auth/refresh` land alongside
                    // their handlers. CSRF stays disabled — JWT bearer auth is
                    // stateless, so the CSRF threat model doesn't apply.
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { rs ->
                rs
                    .bearerTokenConverter(ServerBearerTokenAuthenticationConverter())
                    .jwt { jwt ->
                        jwt
                            .jwtDecoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter)
                    }
            }
            .build()
}
