package com.opencasino.server.security

import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class JwtKeyMaterial(
    val privateKey: RSAPrivateKey,
    val publicKey: RSAPublicKey,
    val keyId: String,
) {
    companion object {
        fun fromPem(privatePem: String, publicPem: String, keyId: String): JwtKeyMaterial {
            require(privatePem.isNotBlank()) {
                "app.jwt.privateKeyPem is not set. Generate a key pair via " +
                    "`openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` " +
                    "and configure it in auth.properties (see auth.properties.example)."
            }
            require(publicPem.isNotBlank()) {
                "app.jwt.publicKeyPem is not set. Derive the public key via " +
                    "`openssl rsa -pubout` from your private key."
            }
            val factory = KeyFactory.getInstance("RSA")
            val privateKey = factory.generatePrivate(
                PKCS8EncodedKeySpec(decode(privatePem, "PRIVATE KEY"))
            ) as RSAPrivateKey
            val publicKey = factory.generatePublic(
                X509EncodedKeySpec(decode(publicPem, "PUBLIC KEY"))
            ) as RSAPublicKey
            return JwtKeyMaterial(privateKey, publicKey, keyId)
        }

        private fun decode(pem: String, label: String): ByteArray {
            val body = pem
                .replace("-----BEGIN $label-----", "")
                .replace("-----END $label-----", "")
                .replace(Regex("\\s+"), "")
            return Base64.getDecoder().decode(body)
        }
    }
}