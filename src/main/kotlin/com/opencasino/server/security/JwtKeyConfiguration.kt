package com.opencasino.server.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(JwtProperties::class, AuthProperties::class)
class JwtKeyConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun jwtKeyMaterial(props: JwtProperties): JwtKeyMaterial =
        JwtKeyMaterial.fromPem(props.privateKeyPem, props.publicKeyPem, props.keyId)
}