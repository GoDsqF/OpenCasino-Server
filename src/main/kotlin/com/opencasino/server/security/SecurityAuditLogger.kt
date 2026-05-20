package com.opencasino.server.security

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SecurityAuditLogger {

    private val log: Logger = LoggerFactory.getLogger("security.audit")

    fun registerSuccess(userId: UUID, email: String, ip: String?, userAgent: String?) =
        emit("auth.register.success", mapOf("userId" to userId, "email" to email, "ip" to ip, "ua" to userAgent))

    fun registerFailure(email: String?, code: AuthFailureCode, ip: String?) =
        emit("auth.register.failure", mapOf("email" to email, "code" to code, "ip" to ip))

    fun loginSuccess(userId: UUID, email: String, ip: String?, userAgent: String?) =
        emit("auth.login.success", mapOf("userId" to userId, "email" to email, "ip" to ip, "ua" to userAgent))

    fun loginFailure(email: String?, code: AuthFailureCode, ip: String?) =
        emit("auth.login.failure", mapOf("email" to email, "code" to code, "ip" to ip))

    fun refreshSuccess(userId: UUID, ip: String?, userAgent: String?) =
        emit("auth.refresh.success", mapOf("userId" to userId, "ip" to ip, "ua" to userAgent))

    fun refreshFailure(code: AuthFailureCode, ip: String?) =
        emit("auth.refresh.failure", mapOf("code" to code, "ip" to ip))

    fun refreshReplay(userId: UUID, ip: String?) =
        emit("auth.refresh.replay_detected", mapOf("userId" to userId, "ip" to ip))

    fun logout(ip: String?) =
        emit("auth.logout", mapOf("ip" to ip))

    fun logoutAll(userId: UUID, count: Long, ip: String?) =
        emit("auth.logout_all", mapOf("userId" to userId, "count" to count, "ip" to ip))

    fun sessionRevoked(userId: UUID, sessionId: UUID, ip: String?) =
        emit("auth.session.revoked", mapOf("userId" to userId, "sessionId" to sessionId, "ip" to ip))

    fun oauthLoginSuccess(provider: String, userId: UUID, ip: String?) =
        emit("oauth.login.success", mapOf("provider" to provider, "userId" to userId, "ip" to ip))

    fun oauthLoginFailure(provider: String?, subject: String?, code: AuthFailureCode, ip: String?) =
        emit("oauth.login.failure", mapOf("provider" to provider, "subject" to subject, "code" to code, "ip" to ip))

    fun rateLimitExceeded(route: String, ip: String?) =
        emit("rate_limit.exceeded", mapOf("route" to route, "ip" to ip))

    fun wsHandshakeAccepted(userId: UUID?, ip: String?) =
        emit("ws.handshake.accepted", mapOf("userId" to userId, "ip" to ip))

    fun wsHandshakeRejected(reason: String, ip: String?) =
        emit("ws.handshake.rejected", mapOf("reason" to reason, "ip" to ip))

    private fun emit(event: String, fields: Map<String, Any?>) {
        val rendered = fields.entries
            .filter { it.value != null }
            .joinToString(" ") { (k, v) -> "$k=${format(v!!)}" }
        log.info("event={} {}", event, rendered)
    }

    private fun format(value: Any): String {
        val s = value.toString()
        return if (s.any { it.isWhitespace() || it == '"' }) "\"" + s.replace("\"", "\\\"") + "\""
        else s
    }
}
