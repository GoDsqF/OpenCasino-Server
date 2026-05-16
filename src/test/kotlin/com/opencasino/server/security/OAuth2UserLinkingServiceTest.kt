package com.opencasino.server.security

import com.opencasino.server.user.User
import com.opencasino.server.user.UserOAuthIdentity
import com.opencasino.server.user.UserOAuthIdentityRepository
import com.opencasino.server.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class OAuth2UserLinkingServiceTest {

    private lateinit var users: UserRepository
    private lateinit var identities: UserOAuthIdentityRepository
    private lateinit var props: AuthProperties
    private lateinit var service: OAuth2UserLinkingService

    @BeforeEach
    fun setUp() {
        users = mock()
        identities = mock()
        props = AuthProperties(displayNameBlocklist = listOf("admin", "root"))
        service = OAuth2UserLinkingService(users, identities, props)

        whenever(identities.findByProviderAndSubject(any(), any())).thenReturn(Mono.empty())
        whenever(users.findByEmail(any())).thenReturn(Mono.empty())
        whenever(users.findById(any())).thenReturn(Mono.empty())
        whenever(users.existsByDisplayName(any())).thenReturn(Mono.just(false))
        whenever(users.updateLastLoginAt(any(), any())).thenReturn(Mono.just(1L))
    }

    @Test
    fun `returning OAuth user is loaded by linked identity`() {
        val user = User(email = "alice@example.com", displayName = "alice")
        whenever(identities.findByProviderAndSubject("google", "sub-1"))
            .thenReturn(Mono.just(UserOAuthIdentity(user.id, "google", "sub-1")))
        whenever(users.findById(user.id)).thenReturn(Mono.just(user))

        StepVerifier.create(service.linkOrCreate(principal(email = "alice@example.com")))
            .assertNext { assertEquals(user.id, it.id) }
            .verifyComplete()

        verify(users, never()).save(any())
        verify(identities, never()).save(any())
    }

    @Test
    fun `unverified email is rejected with OAUTH_EMAIL_UNVERIFIED`() {
        StepVerifier.create(service.linkOrCreate(principal(emailVerified = false)))
            .expectErrorSatisfies {
                assert(it is AuthException && it.failure == AuthFailureCode.OAUTH_EMAIL_UNVERIFIED)
            }
            .verify()

        verify(users, never()).save(any())
        verify(identities, never()).save(any())
    }

    @Test
    fun `missing email is rejected with OAUTH_EMAIL_UNVERIFIED`() {
        StepVerifier.create(service.linkOrCreate(principal(email = null, emailVerified = true)))
            .expectErrorSatisfies {
                assert(it is AuthException && it.failure == AuthFailureCode.OAUTH_EMAIL_UNVERIFIED)
            }
            .verify()
    }

    @Test
    fun `existing local user with same email is linked, not duplicated`() {
        val existing = User(email = "alice@example.com", displayName = "alice", passwordHash = "bcrypt")
        whenever(users.findByEmail("alice@example.com")).thenReturn(Mono.just(existing))
        whenever(identities.save(any()))
            .thenReturn(Mono.just(UserOAuthIdentity(existing.id, "google", "sub-1")))

        StepVerifier.create(service.linkOrCreate(principal(email = "alice@example.com")))
            .assertNext { assertEquals(existing.id, it.id) }
            .verifyComplete()

        val captor = argumentCaptor<UserOAuthIdentity>()
        verify(identities).save(captor.capture())
        assertEquals(existing.id, captor.firstValue.userId)
        assertEquals("google", captor.firstValue.provider)
        assertEquals("sub-1", captor.firstValue.subject)
        verify(users, never()).save(any())
    }

    @Test
    fun `email is matched case-insensitively against existing user`() {
        val existing = User(email = "alice@example.com", displayName = "alice")
        whenever(users.findByEmail("alice@example.com")).thenReturn(Mono.just(existing))
        whenever(identities.save(any()))
            .thenReturn(Mono.just(UserOAuthIdentity(existing.id, "google", "sub-1")))

        StepVerifier.create(service.linkOrCreate(principal(email = "ALICE@example.com")))
            .assertNext { assertEquals(existing.id, it.id) }
            .verifyComplete()
    }

    @Test
    fun `new user is created when no existing email matches`() {
        whenever(users.save(any())).thenAnswer { Mono.just(it.arguments[0] as User) }
        whenever(identities.save(any())).thenAnswer { Mono.just(it.arguments[0] as UserOAuthIdentity) }

        StepVerifier.create(service.linkOrCreate(principal(email = "newbie@example.com", profileName = "Newbie Smith")))
            .assertNext { user ->
                assertEquals("newbie@example.com", user.email)
                assertEquals("NewbieSmith", user.displayName)
                assertNull(user.passwordHash)
                assertNotNull(user.id)
            }
            .verifyComplete()

        val identityCaptor = argumentCaptor<UserOAuthIdentity>()
        verify(identities).save(identityCaptor.capture())
        assertEquals("google", identityCaptor.firstValue.provider)
        assertEquals("sub-1", identityCaptor.firstValue.subject)
    }

    @Test
    fun `display-name collision is resolved by numeric suffix`() {
        whenever(users.existsByDisplayName("alice")).thenReturn(Mono.just(true))
        whenever(users.existsByDisplayName("alice-2")).thenReturn(Mono.just(true))
        whenever(users.existsByDisplayName("alice-3")).thenReturn(Mono.just(false))
        whenever(users.save(any())).thenAnswer { Mono.just(it.arguments[0] as User) }
        whenever(identities.save(any())).thenAnswer { Mono.just(it.arguments[0] as UserOAuthIdentity) }

        StepVerifier.create(service.linkOrCreate(principal(email = "alice@example.com", profileName = "alice")))
            .assertNext { user -> assertEquals("alice-3", user.displayName) }
            .verifyComplete()
    }

    @Test
    fun `denylisted profile name falls back to email local-part`() {
        whenever(users.save(any())).thenAnswer { Mono.just(it.arguments[0] as User) }
        whenever(identities.save(any())).thenAnswer { Mono.just(it.arguments[0] as UserOAuthIdentity) }

        StepVerifier.create(service.linkOrCreate(principal(email = "carol@example.com", profileName = "admin")))
            .assertNext { user -> assertEquals("carol", user.displayName) }
            .verifyComplete()
    }

    private fun principal(
        provider: String = "google",
        subject: String = "sub-1",
        email: String? = "user@example.com",
        emailVerified: Boolean = true,
        profileName: String? = null,
    ) = OAuth2UserPrincipal(provider, subject, email, emailVerified, profileName)
}
