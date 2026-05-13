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
                    ).permitAll()
                    // TODO(Auth phase 3-5): add allowlist entries alongside the
                    // handlers that need them — `/auth/register`, `/auth/login`,
                    // `/oauth2/authorize/{provider}`, `/oauth2/redirect/{provider}`,
                    // `/auth/refresh`. Do not pre-open paths without handlers.
                    .anyExchange().authenticated()
            }
            .build()
}
