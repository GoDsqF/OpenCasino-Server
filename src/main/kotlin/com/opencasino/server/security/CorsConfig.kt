package com.opencasino.server.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig {

    @Bean
    fun corsConfigurationSource(props: CorsProperties): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = props.allowedOrigins.filter { it.isNotBlank() }
            allowedOriginPatterns = props.allowedOriginPatterns.filter { it.isNotBlank() }
            allowedMethods = props.allowedMethods
            allowedHeaders = props.allowedHeaders
            exposedHeaders = props.exposedHeaders
            // CORS spec forbids credentials + wildcard origins together. We honour the
            // configured flag, but Spring will refuse to emit an unsafe combo at runtime.
            allowCredentials = props.allowCredentials && (allowedOrigins?.isNotEmpty() == true || allowedOriginPatterns?.isNotEmpty() == true)
            maxAge = props.maxAge.seconds
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
