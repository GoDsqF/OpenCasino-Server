package com.opencasino.server.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.ratelimit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val login: BucketSpec = BucketSpec(capacity = 20, refill = 20, period = Duration.ofMinutes(1)),
    val register: BucketSpec = BucketSpec(capacity = 5, refill = 5, period = Duration.ofHours(1)),
    val refresh: BucketSpec = BucketSpec(capacity = 60, refill = 60, period = Duration.ofMinutes(1)),
    val wsHandshake: BucketSpec = BucketSpec(capacity = 30, refill = 30, period = Duration.ofMinutes(1)),
    val authenticated: BucketSpec = BucketSpec(capacity = 600, refill = 600, period = Duration.ofMinutes(1)),
) {
    data class BucketSpec(
        val capacity: Long,
        val refill: Long,
        val period: Duration,
    )
}
