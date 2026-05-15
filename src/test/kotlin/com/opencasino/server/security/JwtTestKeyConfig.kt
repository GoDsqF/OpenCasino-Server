package com.opencasino.server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Configuration
class JwtTestKeyConfig {

    @Bean
    fun jwtKeyMaterial(): JwtKeyMaterial {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return JwtKeyMaterial(
            privateKey = pair.private as RSAPrivateKey,
            publicKey = pair.public as RSAPublicKey,
            keyId = "test-key",
        )
    }
}
