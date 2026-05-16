package com.opencasino.server.security

import com.opencasino.server.user.User
import com.opencasino.server.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenService: RefreshTokenService,
    private val authProperties: AuthProperties,
) {

    private val dummyHash: String by lazy { passwordEncoder.encode("never-matches-anything") }

    fun register(request: RegisterRequest): Mono<RegisterResponse> {
        val email = normalizeEmail(request.email)
            ?: return Mono.error(AuthException(AuthFailureCode.INVALID_EMAIL))
        val password = request.password
            ?: return Mono.error(AuthException(AuthFailureCode.WEAK_PASSWORD))
        if (!isAcceptablePassword(password)) {
            return Mono.error(AuthException(AuthFailureCode.WEAK_PASSWORD))
        }
        val displayName = normalizeDisplayName(request.displayName)
            ?: return Mono.error(AuthException(AuthFailureCode.INVALID_DISPLAY_NAME))

        return users.findByEmail(email)
            .flatMap<User> { Mono.error(AuthException(AuthFailureCode.EMAIL_TAKEN)) }
            .switchIfEmpty(
                Mono.fromCallable { passwordEncoder.encode(password) }
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap { hash ->
                        users.save(User(email = email, passwordHash = hash, displayName = displayName))
                    }
                    .onErrorMap(DuplicateKeyException::class.java) { AuthException(AuthFailureCode.EMAIL_TAKEN) }
                    .onErrorMap(DataIntegrityViolationException::class.java) { AuthException(AuthFailureCode.EMAIL_TAKEN) }
            )
            .map { user -> RegisterResponse(userId = user.id, email = user.email, displayName = user.displayName) }
    }

    fun login(request: LoginRequest): Mono<LoginResponse> {
        val email = normalizeEmail(request.email)
        val password = request.password
        if (email == null || password.isNullOrEmpty()) {
            return rejectAfterEqualizingTiming(password.orEmpty())
        }

        return users.findByEmail(email)
            .flatMap { user ->
                val hash = user.passwordHash
                    ?: return@flatMap rejectAfterEqualizingTiming(password)
                Mono.fromCallable { passwordEncoder.matches(password, hash) }
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap { ok ->
                        if (ok) buildLoginResponse(user)
                        else Mono.error(AuthException(AuthFailureCode.INVALID_CREDENTIALS))
                    }
            }
            .switchIfEmpty(rejectAfterEqualizingTiming(password))
    }

    private fun rejectAfterEqualizingTiming(password: String): Mono<LoginResponse> =
        Mono.fromCallable { passwordEncoder.matches(password, dummyHash) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { Mono.error(AuthException(AuthFailureCode.INVALID_CREDENTIALS)) }

    fun refresh(request: RefreshRequest): Mono<LoginResponse> {
        val plaintext = request.refreshToken
            ?: return Mono.error(AuthException(AuthFailureCode.REFRESH_INVALID))
        return refreshTokenService.rotate(plaintext)
            .flatMap { rotated ->
                users.findById(rotated.userId)
                    .switchIfEmpty(Mono.error(AuthException(AuthFailureCode.REFRESH_INVALID)))
                    .map { user -> assembleLoginResponse(user, rotated.refresh) }
            }
    }

    fun logout(request: LogoutRequest): Mono<Void> {
        val plaintext = request.refreshToken
            ?: return Mono.error(AuthException(AuthFailureCode.REFRESH_INVALID))
        return refreshTokenService.revoke(plaintext)
    }

    private fun buildLoginResponse(user: User): Mono<LoginResponse> =
        refreshTokenService.issue(user.id)
            .map { issuedRefresh -> assembleLoginResponse(user, issuedRefresh) }

    private fun assembleLoginResponse(user: User, issuedRefresh: IssuedRefresh): LoginResponse {
        val issued = jwtIssuer.issueAccess(user)
        return LoginResponse(
            userId = user.id,
            accessToken = issued.token,
            refreshToken = issuedRefresh.plaintext,
            expiresAt = issued.expiresAt,
            refreshExpiresAt = issuedRefresh.expiresAt,
        )
    }

    private fun normalizeEmail(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim().lowercase()
        return if (EMAIL_PATTERN.matches(trimmed)) trimmed else null
    }

    private fun isAcceptablePassword(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH

    private fun normalizeDisplayName(raw: String?): String? = normalizeDisplayName(raw, authProperties.displayNameBlocklist)

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MIN_DISPLAY_NAME_LENGTH = 3
        const val MAX_DISPLAY_NAME_LENGTH = 32
        const val DEFAULT_OAUTH_DISPLAY_NAME = "user"
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        private val DISPLAY_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")
        private val NON_DISPLAY_NAME_CHARS = Regex("[^A-Za-z0-9_-]")

        fun normalizeDisplayName(raw: String?, blocklist: List<String>): String? {
            val trimmed = raw?.trim() ?: return null
            if (trimmed.length !in MIN_DISPLAY_NAME_LENGTH..MAX_DISPLAY_NAME_LENGTH) return null
            if (!DISPLAY_NAME_PATTERN.matches(trimmed)) return null
            val lower = trimmed.lowercase()
            if (blocklist.any { it.isNotBlank() && lower.contains(it.lowercase()) }) return null
            return trimmed
        }

        // Best-effort display-name candidate from an OAuth provider profile.
        // Tries the provider-supplied name first (sanitized), then the email
        // local-part, then a generic last-resort default. Result is guaranteed
        // non-null and 3..32 [A-Za-z0-9_-]+, but uniqueness is not guaranteed —
        // callers should disambiguate against existing rows (suffix or retry).
        fun deriveDisplayNameForOAuth(profileName: String?, email: String?, blocklist: List<String>): String {
            val candidates = sequenceOf(
                profileName,
                email?.substringBefore('@')?.takeIf { it.isNotBlank() },
            )
            return candidates
                .mapNotNull { sanitizeForDisplay(it) }
                .firstOrNull { normalizeDisplayName(it, blocklist) != null }
                ?: DEFAULT_OAUTH_DISPLAY_NAME
        }

        private fun sanitizeForDisplay(raw: String?): String? {
            if (raw == null) return null
            val cleaned = raw.replace(NON_DISPLAY_NAME_CHARS, "").take(MAX_DISPLAY_NAME_LENGTH)
            return cleaned.takeIf { it.length >= MIN_DISPLAY_NAME_LENGTH }
        }
    }
}
