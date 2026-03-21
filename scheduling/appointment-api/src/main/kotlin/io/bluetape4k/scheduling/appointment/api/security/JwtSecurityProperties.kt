package io.bluetape4k.scheduling.appointment.api.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 보안 설정 프로퍼티.
 *
 * ```yaml
 * scheduling:
 *   security:
 *     jwt:
 *       enabled: true
 *       secret: base64-encoded-secret-key-at-least-256-bits
 *       issuer: appointment-auth-service
 * ```
 */
@ConfigurationProperties(prefix = "scheduling.security.jwt")
data class JwtSecurityProperties(
    val enabled: Boolean = true,
    val secret: String = "",
    val issuer: String = "appointment-auth-service",
)
