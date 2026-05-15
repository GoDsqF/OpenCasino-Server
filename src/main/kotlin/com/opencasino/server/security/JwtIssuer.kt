package com.opencasino.server.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.opencasino.server.user.User
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.Date

@Service
class JwtIssuer(
    private val props: JwtProperties,
    private val keys: JwtKeyMaterial,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val signer = RSASSASigner(keys.privateKey)
    private val header = JWSHeader.Builder(JWSAlgorithm.RS256)
        .type(JOSEObjectType.JWT)
        .keyID(keys.keyId)
        .build()

    fun issueAccess(user: User): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(props.accessTtl)
        val claims = JWTClaimsSet.Builder()
            .issuer(props.issuer)
            .subject(user.id.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .claim(CLAIM_EMAIL, user.email)
            .claim(CLAIM_ROLES, listOf(user.role.name))
            .build()
        val jwt = SignedJWT(header, claims).apply { sign(signer) }
        return IssuedToken(jwt.serialize(), expiresAt)
    }

    companion object {
        const val CLAIM_EMAIL = "email"
        const val CLAIM_ROLES = "roles"
    }
}

data class IssuedToken(val token: String, val expiresAt: Instant)