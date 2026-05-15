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

        return users.findByEmail(email)
            .flatMap<User> { Mono.error(AuthException(AuthFailureCode.EMAIL_TAKEN)) }
            .switchIfEmpty(
                Mono.fromCallable { passwordEncoder.encode(password) }
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap { hash -> users.save(User(email = email, passwordHash = hash)) }
                    .onErrorMap(DuplicateKeyException::class.java) { AuthException(AuthFailureCode.EMAIL_TAKEN) }
                    .onErrorMap(DataIntegrityViolationException::class.java) { AuthException(AuthFailureCode.EMAIL_TAKEN) }
            )
            .map { user -> RegisterResponse(userId = user.id, email = user.email) }
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
                        if (ok) Mono.just(buildLoginResponse(user))
                        else Mono.error(AuthException(AuthFailureCode.INVALID_CREDENTIALS))
                    }
            }
            .switchIfEmpty(rejectAfterEqualizingTiming(password))
    }

    private fun rejectAfterEqualizingTiming(password: String): Mono<LoginResponse> =
        Mono.fromCallable { passwordEncoder.matches(password, dummyHash) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { Mono.error(AuthException(AuthFailureCode.INVALID_CREDENTIALS)) }

    private fun buildLoginResponse(user: User): LoginResponse {
        val issued = jwtIssuer.issueAccess(user)
        return LoginResponse(
            userId = user.id,
            accessToken = issued.token,
            refreshToken = "stub-refresh-${user.id}",
            expiresAt = issued.expiresAt,
        )
    }

    private fun normalizeEmail(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim().lowercase()
        return if (EMAIL_PATTERN.matches(trimmed)) trimmed else null
    }

    private fun isAcceptablePassword(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}