package com.opencasino.server.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
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

    @Test
    fun `fromProperties reads PEM from privateKeyPath and publicKeyPath`(@TempDir tmp: Path) {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privFile = tmp.resolve("jwt-private.pem")
        val pubFile = tmp.resolve("jwt-public.pem")
        Files.writeString(privFile, toPem("PRIVATE KEY", pair.private.encoded))
        Files.writeString(pubFile, toPem("PUBLIC KEY", pair.public.encoded))

        val material = JwtKeyMaterial.fromProperties(
            JwtProperties(
                keyId = "kid-file",
                privateKeyPath = privFile.toString(),
                publicKeyPath = pubFile.toString(),
            )
        )

        assertEquals("kid-file", material.keyId)
        assertEquals(
            (pair.private as RSAPrivateKey).modulus,
            material.privateKey.modulus,
        )
    }

    @Test
    fun `fromProperties prefers inline PEM over file path`(@TempDir tmp: Path) {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privPem = toPem("PRIVATE KEY", pair.private.encoded)
        val pubPem = toPem("PUBLIC KEY", pair.public.encoded)
        val bogusPath = tmp.resolve("does-not-exist.pem").toString()

        val material = JwtKeyMaterial.fromProperties(
            JwtProperties(
                privateKeyPem = privPem,
                publicKeyPem = pubPem,
                privateKeyPath = bogusPath,
                publicKeyPath = bogusPath,
            )
        )

        assertNotNull(material)
    }

    @Test
    fun `fromProperties fails clearly when keyPath points to missing file`(@TempDir tmp: Path) {
        val missing = tmp.resolve("missing.pem").toString()
        val ex = assertThrows<IllegalArgumentException> {
            JwtKeyMaterial.fromProperties(
                JwtProperties(privateKeyPath = missing, publicKeyPath = missing)
            )
        }
        assertEquals(true, ex.message!!.contains("missing.pem"))
    }

    @Test
    fun `fromProperties fails clearly when keyPath points to unreadable file`(@TempDir tmp: Path) {
        val unreadable = tmp.resolve("locked.pem")
        Files.writeString(unreadable, "irrelevant")
        val perms = java.nio.file.attribute.PosixFilePermissions.fromString("---------")
        try {
            Files.setPosixFilePermissions(unreadable, perms)
        } catch (_: UnsupportedOperationException) {
            return
        }
        if (Files.isReadable(unreadable)) return

        val ex = assertThrows<IllegalArgumentException> {
            JwtKeyMaterial.fromProperties(
                JwtProperties(
                    privateKeyPath = unreadable.toString(),
                    publicKeyPath = unreadable.toString(),
                )
            )
        }
        assertEquals(true, ex.message!!.contains("not readable"))
        assertEquals(true, ex.message!!.contains("APP_UID"))
    }

    private fun toPem(label: String, encoded: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return "-----BEGIN $label-----\n$body\n-----END $label-----\n"
    }
}