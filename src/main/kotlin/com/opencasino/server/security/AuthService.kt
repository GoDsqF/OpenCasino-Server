package com.opencasino.server.security

import com.opencasino.server.user.User
import com.opencasino.server.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

@Service
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenService: RefreshTokenService,
    private val authProperties: AuthProperties,
    private val auditLogger: SecurityAuditLogger,
) {

    private val dummyHash: String by lazy { passwordEncoder.encode("never-matches-anything") }

    fun register(request: RegisterRequest, context: ClientContext = ClientContext.EMPTY): Mono<RegisterResponse> {
        val email = normalizeEmail(request.email)
            ?: return failWith(AuthFailureCode.INVALID_EMAIL) {
                auditLogger.registerFailure(request.email, AuthFailureCode.INVALID_EMAIL, context.ip)
            }
        val password = request.password
            ?: return failWith(AuthFailureCode.WEAK_PASSWORD) {
                auditLogger.registerFailure(email, AuthFailureCode.WEAK_PASSWORD, context.ip)
            }
        if (!isAcceptablePassword(password)) {
            return failWith(AuthFailureCode.WEAK_PASSWORD) {
                auditLogger.registerFailure(email, AuthFailureCode.WEAK_PASSWORD, context.ip)
            }
        }
        val displayName = normalizeDisplayName(request.displayName)
            ?: return failWith(AuthFailureCode.INVALID_DISPLAY_NAME) {
                auditLogger.registerFailure(email, AuthFailureCode.INVALID_DISPLAY_NAME, context.ip)
            }

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
            .doOnNext { response -> auditLogger.registerSuccess(response.userId, response.email, context.ip, context.userAgent) }
            .doOnError(AuthException::class.java) { ex -> auditLogger.registerFailure(email, ex.failure, context.ip) }
    }

    private fun <T> failWith(code: AuthFailureCode, audit: () -> Unit): Mono<T> {
        audit()
        return Mono.error(AuthException(code))
    }

    fun login(request: LoginRequest, context: ClientContext = ClientContext.EMPTY): Mono<LoginResponse> {
        val email = normalizeEmail(request.email)
        val password = request.password
        if (email == null || password.isNullOrEmpty()) {
            return rejectAfterEqualizingTiming(password.orEmpty(), email, context)
        }

        return users.findByEmail(email)
            .flatMap { user ->
                val hash = user.passwordHash
                    ?: return@flatMap rejectAfterEqualizingTiming(password, email, context)
                Mono.fromCallable { passwordEncoder.matches(password, hash) }
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap { ok ->
                        if (ok) {
                            buildLoginResponse(user, context)
                                .doOnNext { auditLogger.loginSuccess(user.id, user.email, context.ip, context.userAgent) }
                        } else {
                            auditLogger.loginFailure(email, AuthFailureCode.INVALID_CREDENTIALS, context.ip)
                            Mono.error(AuthException(AuthFailureCode.INVALID_CREDENTIALS))
                        }
                    }
            }
            .switchIfEmpty(rejectAfterEqualizingTiming(password, email, context))
    }

    private fun rejectAfterEqualizingTiming(password: String, email: String?, context: ClientContext): Mono<LoginResponse> =
        Mono.fromCallable { passwordEncoder.matches(password, dummyHash) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap {
                auditLogger.loginFailure(email, AuthFailureCode.INVALID_CREDENTIALS, context.ip)
                Mono.error(AuthException(AuthFailureCode.INVALID_CREDENTIALS))
            }

    fun refresh(request: RefreshRequest, context: ClientContext = ClientContext.EMPTY): Mono<LoginResponse> {
        val plaintext = request.refreshToken
            ?: return failWith(AuthFailureCode.REFRESH_INVALID) {
                auditLogger.refreshFailure(AuthFailureCode.REFRESH_INVALID, context.ip)
            }
        return refreshTokenService.rotate(plaintext, context.userAgent, context.ip)
            .flatMap { rotated ->
                users.findById(rotated.userId)
                    .switchIfEmpty(Mono.error(AuthException(AuthFailureCode.REFRESH_INVALID)))
                    .map { user -> assembleLoginResponse(user, rotated.refresh) }
                    .doOnNext { auditLogger.refreshSuccess(rotated.userId, context.ip, context.userAgent) }
            }
            .doOnError(AuthException::class.java) { ex ->
                if (ex.failure != AuthFailureCode.REFRESH_REPLAY_DETECTED) {
                    auditLogger.refreshFailure(ex.failure, context.ip)
                }
                // REFRESH_REPLAY_DETECTED is logged from RefreshTokenService where the userId is known.
            }
    }

    fun logout(request: LogoutRequest, context: ClientContext = ClientContext.EMPTY): Mono<Void> {
        val plaintext = request.refreshToken
            ?: return failWith(AuthFailureCode.REFRESH_INVALID) {
                auditLogger.refreshFailure(AuthFailureCode.REFRESH_INVALID, context.ip)
            }
        return refreshTokenService.revoke(plaintext)
            .doOnSuccess { auditLogger.logout(context.ip) }
    }

    private fun buildLoginResponse(user: User, context: ClientContext): Mono<LoginResponse> =
        users.updateLastLoginAt(user.id, Instant.now())
            .then(refreshTokenService.issue(user.id, context.userAgent, context.ip))
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
