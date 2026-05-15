package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class JwtKeyMaterialTest {

    @Test
    fun `parses PKCS8 private and X509 public PEM`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privPem = toPem("PRIVATE KEY", pair.private.encoded)
        val pubPem = toPem("PUBLIC KEY", pair.public.encoded)

        val material = JwtKeyMaterial.fromPem(privPem, pubPem, "kid-1")

        assertEquals("kid-1", material.keyId)
        assertEquals(
            (pair.private as RSAPrivateKey).modulus,
            material.privateKey.modulus,
        )
        assertEquals(
            (pair.public as RSAPublicKey).modulus,
            material.publicKey.modulus,
        )
        assertNotNull(material)
    }

    @Test
    fun `empty private PEM is rejected with helpful message`() {
        val ex = assertThrows<IllegalArgumentException> {
            JwtKeyMaterial.fromPem("", "anything", "kid")
        }
        assertEquals(true, ex.message!!.contains("openssl genpkey"))
    }

    @Test
    fun `empty public PEM is rejected`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privPem = toPem("PRIVATE KEY", pair.private.encoded)
        assertThrows<IllegalArgumentException> {
            JwtKeyMaterial.fromPem(privPem, "", "kid")
        }
    }

    private fun toPem(label: String, encoded: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }
}