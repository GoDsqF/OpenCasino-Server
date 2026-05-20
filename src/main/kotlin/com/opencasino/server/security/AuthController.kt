package com.opencasino.server.security

import com.opencasino.server.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val clientIpResolver: ClientIpResolver,
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest, exchange: ServerWebExchange): Mono<ResponseEntity<Any>> =
        authService.register(request, ClientContext.from(exchange, clientIpResolver))
            .map { body -> ResponseEntity.status(HttpStatus.CREATED).body(body as Any) }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, exchange: ServerWebExchange): Mono<ResponseEntity<Any>> =
        authService.login(request, ClientContext.from(exchange, clientIpResolver))
            .map { body -> ResponseEntity.ok(body as Any) }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest, exchange: ServerWebExchange): Mono<ResponseEntity<Any>> =
        authService.refresh(request, ClientContext.from(exchange, clientIpResolver))
            .map { body -> ResponseEntity.ok(body as Any) }

    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest, exchange: ServerWebExchange): Mono<ResponseEntity<Void>> =
        authService.logout(request, ClientContext.from(exchange, clientIpResolver))
            .then(Mono.fromCallable { ResponseEntity.noContent().build<Void>() })

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): Mono<MeResponse> {
        @Suppress("UNCHECKED_CAST")
        val roles = (jwt.claims[JwtIssuer.CLAIM_ROLES] as? List<String>) ?: emptyList()
        val userId = UUID.fromString(jwt.subject)
        return userRepository.findById(userId).map { user ->
            MeResponse(
                userId = userId,
                email = jwt.getClaimAsString(JwtIssuer.CLAIM_EMAIL) ?: user.email,
                displayName = user.displayName,
                roles = roles,
                balance = user.balance,
            )
        }
    }

    @ExceptionHandler(AuthException::class)
    fun handleAuthFailure(ex: AuthException): ResponseEntity<AuthFailureBody> {
        val status = when (ex.failure) {
            AuthFailureCode.INVALID_EMAIL,
            AuthFailureCode.WEAK_PASSWORD,
            AuthFailureCode.INVALID_DISPLAY_NAME,
            AuthFailureCode.MALFORMED_REQUEST -> HttpStatus.BAD_REQUEST
            AuthFailureCode.EMAIL_TAKEN -> HttpStatus.CONFLICT
            AuthFailureCode.INVALID_CREDENTIALS,
            AuthFailureCode.OAUTH_EMAIL_UNVERIFIED,
            AuthFailureCode.REFRESH_INVALID,
            AuthFailureCode.REFRESH_EXPIRED,
            AuthFailureCode.REFRESH_REVOKED,
            AuthFailureCode.REFRESH_REPLAY_DETECTED -> HttpStatus.UNAUTHORIZED
            AuthFailureCode.OAUTH_PROVIDER_ERROR -> HttpStatus.BAD_GATEWAY
        }
        return ResponseEntity.status(status).body(
            AuthFailureBody(code = ex.failure.name, message = messageFor(ex.failure))
        )
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleMalformedBody(ex: ServerWebInputException): ResponseEntity<AuthFailureBody> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            AuthFailureBody(
                code = AuthFailureCode.MALFORMED_REQUEST.name,
                message = "Request body could not be parsed.",
            )
        )

    private fun messageFor(code: AuthFailureCode): String = when (code) {
        AuthFailureCode.INVALID_EMAIL -> "Email is missing or not a valid address."
        AuthFailureCode.WEAK_PASSWORD ->
            "Password must be at least ${AuthService.MIN_PASSWORD_LENGTH} characters."
        AuthFailureCode.INVALID_DISPLAY_NAME ->
            "Display name must be ${AuthService.MIN_DISPLAY_NAME_LENGTH}–${AuthService.MAX_DISPLAY_NAME_LENGTH}" +
                " characters of [A-Za-z0-9_-] and not contain reserved substrings."
        AuthFailureCode.MALFORMED_REQUEST -> "Request body could not be parsed."
        AuthFailureCode.EMAIL_TAKEN -> "An account with this email already exists."
        AuthFailureCode.INVALID_CREDENTIALS -> "Email or password is incorrect."
        AuthFailureCode.OAUTH_EMAIL_UNVERIFIED ->
            "OAuth provider did not return a verified email."
        AuthFailureCode.OAUTH_PROVIDER_ERROR -> "OAuth provider exchange failed."
        AuthFailureCode.REFRESH_INVALID -> "Refresh token is missing or unknown."
        AuthFailureCode.REFRESH_EXPIRED -> "Refresh token has expired."
        AuthFailureCode.REFRESH_REVOKED -> "Refresh token has been revoked."
        AuthFailureCode.REFRESH_REPLAY_DETECTED ->
            "Refresh token reuse detected; all sessions for this account have been revoked."
    }
}
