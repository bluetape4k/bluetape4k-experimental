package io.bluetape4k.scheduling.appointment.notification

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 알림 채널 Resilience4j 설정 프로퍼티.
 *
 * ```yaml
 * scheduling:
 *   notification:
 *     resilience:
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *         slow-call-rate-threshold: 80
 *         wait-duration-in-open-state: 30s
 *         sliding-window-size: 10
 *         minimum-number-of-calls: 5
 *       retry:
 *         max-attempts: 3
 *         wait-duration: 500ms
 *       bulkhead:
 *         max-concurrent-calls: 10
 *         max-wait-duration: 1s
 * ```
 */
@ConfigurationProperties(prefix = "scheduling.notification.resilience")
data class NotificationResilienceProperties(
    val circuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
    val retry: RetryProperties = RetryProperties(),
    val bulkhead: BulkheadProperties = BulkheadProperties(),
) {
    data class CircuitBreakerProperties(
        val failureRateThreshold: Float = 50f,
        val slowCallRateThreshold: Float = 80f,
        val waitDurationInOpenState: Duration = Duration.ofSeconds(30),
        val slidingWindowSize: Int = 10,
        val minimumNumberOfCalls: Int = 5,
    )

    data class RetryProperties(
        val maxAttempts: Int = 3,
        val waitDuration: Duration = Duration.ofMillis(500),
    )

    data class BulkheadProperties(
        val maxConcurrentCalls: Int = 10,
        val maxWaitDuration: Duration = Duration.ofSeconds(1),
    )
}
