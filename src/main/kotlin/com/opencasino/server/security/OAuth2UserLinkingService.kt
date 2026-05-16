package com.opencasino.server.security

import com.opencasino.server.user.User
import com.opencasino.server.user.UserOAuthIdentity
import com.opencasino.server.user.UserOAuthIdentityRepository
import com.opencasino.server.user.UserRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

data class OAuth2UserPrincipal(
    val provider: String,
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val profileName: String?,
)

@Service
class OAuth2UserLinkingService(
    private val users: UserRepository,
    private val identities: UserOAuthIdentityRepository,
    private val authProperties: AuthProperties,
) {

    // Phase 6 contract:
    //   1. (provider, subject) already linked    -> return that user
    //   2. provider didn't return verified email -> AuthException(OAUTH_EMAIL_UNVERIFIED)
    //   3. existing user with that email         -> link new identity, return user
    //   4. otherwise                             -> create user + identity
    //
    // Step 3 is safe under "all email_verified=true" (Phase 6 migration 004):
    // every account is trusted to own its email at create time, so OAuth
    // proving ownership of the same email is a legitimate link, not takeover.
    fun linkOrCreate(principal: OAuth2UserPrincipal): Mono<User> =
        identities.findByProviderAndSubject(principal.provider, principal.subject)
            .flatMap { existing -> users.findById(existing.userId) }
            .switchIfEmpty(Mono.defer { linkOrCreateForFreshIdentity(principal) })

    private fun linkOrCreateForFreshIdentity(principal: OAuth2UserPrincipal): Mono<User> {
        if (!principal.emailVerified || principal.email.isNullOrBlank()) {
            return Mono.error(AuthException(AuthFailureCode.OAUTH_EMAIL_UNVERIFIED))
        }
        val email = principal.email.lowercase()
        return users.findByEmail(email)
            .flatMap { existing -> linkExisting(principal, existing) }
            .switchIfEmpty(Mono.defer { createUserAndIdentity(principal, email) })
    }

    private fun linkExisting(principal: OAuth2UserPrincipal, user: User): Mono<User> =
        identities.save(
            UserOAuthIdentity(userId = user.id, provider = principal.provider, subject = principal.subject)
        ).thenReturn(user)

    private fun createUserAndIdentity(principal: OAuth2UserPrincipal, email: String): Mono<User> =
        freshDisplayName(principal.profileName, email)
            .flatMap { displayName ->
                users.save(
                    User(email = email, passwordHash = null, displayName = displayName)
                )
            }
            .flatMap { user ->
                identities.save(
                    UserOAuthIdentity(userId = user.id, provider = principal.provider, subject = principal.subject)
                ).thenReturn(user)
            }

    private fun freshDisplayName(profileName: String?, email: String): Mono<String> {
        val base = AuthService.deriveDisplayNameForOAuth(profileName, email, authProperties.displayNameBlocklist)
        return tryWithSuffix(base, attempt = 1)
    }

    private fun tryWithSuffix(base: String, attempt: Int): Mono<String> {
        if (attempt > MAX_DISPLAY_NAME_SUFFIX_ATTEMPTS) {
            return Mono.error(AuthException(AuthFailureCode.OAUTH_PROVIDER_ERROR))
        }
        val candidate = if (attempt == 1) base else withSuffix(base, attempt)
        return users.existsByDisplayName(candidate).flatMap { exists ->
            if (!exists) Mono.just(candidate) else tryWithSuffix(base, attempt + 1)
        }
    }

    private fun withSuffix(base: String, attempt: Int): String {
        val suffix = "-$attempt"
        val budget = AuthService.MAX_DISPLAY_NAME_LENGTH - suffix.length
        return base.take(budget) + suffix
    }

    companion object {
        private const val MAX_DISPLAY_NAME_SUFFIX_ATTEMPTS = 100
    }
}
