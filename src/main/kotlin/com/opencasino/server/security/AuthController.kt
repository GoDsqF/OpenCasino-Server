package com.opencasino.server.security

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
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): Mono<ResponseEntity<Any>> =
        authService.register(request)
            .map { body -> ResponseEntity.status(HttpStatus.CREATED).body(body as Any) }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): Mono<ResponseEntity<Any>> =
        authService.login(request)
            .map { body -> ResponseEntity.ok(body as Any) }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse {
        @Suppress("UNCHECKED_CAST")
        val roles = (jwt.claims[JwtIssuer.CLAIM_ROLES] as? List<String>) ?: emptyList()
        return MeResponse(
            userId = UUID.fromString(jwt.subject),
            email = jwt.getClaimAsString(JwtIssuer.CLAIM_EMAIL),
            roles = roles,
        )
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
            AuthFailureCode.OAUTH_EMAIL_UNVERIFIED -> HttpStatus.UNAUTHORIZED
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
    }
}
