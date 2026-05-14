package com.opencasino.server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@Profile("!test")
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
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
                    // TODO(Auth phase 4-5): add `/auth/refresh`,
                    // `/oauth2/authorize/{provider}`, `/oauth2/redirect/{provider}`
                    // alongside their handlers. CSRF stays disabled — JWT bearer
                    // auth is stateless, so the CSRF threat model doesn't apply.
                    .anyExchange().authenticated()
            }
            .build()
}
