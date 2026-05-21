package com.opencasino.server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import reactor.core.publisher.Mono

@Configuration
@Profile("!test")
class JwtAuthenticationConfiguration {

    @Bean
    fun reactiveJwtDecoder(keys: JwtKeyMaterial, props: JwtProperties): ReactiveJwtDecoder {
        val decoder = NimbusReactiveJwtDecoder.withPublicKey(keys.publicKey).build()
        decoder.setJwtValidator(buildValidator(props.issuer))
        return decoder
    }

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName(JwtIssuer.CLAIM_ROLES)
            setAuthorityPrefix("ROLE_")
        }
        return Converter { jwt ->
            val authorities = authoritiesConverter.convert(jwt) ?: emptyList()
            Mono.just(JwtAuthenticationToken(jwt, authorities, jwt.subject!!))
        }
    }

    private fun buildValidator(issuer: String): OAuth2TokenValidator<Jwt> {
        val defaults = JwtValidators.createDefault()
        val issuerCheck = JwtClaimValidator<String>(JwtClaimNames.ISS) { it == issuer }
        return org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator(defaults, issuerCheck)
    }
}