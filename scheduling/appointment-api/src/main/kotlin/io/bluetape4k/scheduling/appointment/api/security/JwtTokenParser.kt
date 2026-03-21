package io.bluetape4k.scheduling.appointment.api.security

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Base64

/**
 * JWT 토큰 파싱 및 검증.
 *
 * 외부 인증 서비스가 발급한 JWT를 검증하고 Claims를 추출합니다.
 */
class JwtTokenParser(
    private val properties: JwtSecurityProperties,
) {
    companion object : KLogging() {
        private const val CLAIM_CLINIC_ID = "clinicId"
        private const val CLAIM_ROLES = "roles"
    }

    private val signingKey by lazy {
        val keyBytes = Base64.getDecoder().decode(properties.secret)
        Keys.hmacShaKeyFor(keyBytes)
    }

    /**
     * JWT 토큰을 파싱하여 [SchedulingUserPrincipal]을 반환합니다.
     *
     * @return 유효한 토큰이면 [SchedulingUserPrincipal], 아니면 null
     */
    fun parse(token: String): SchedulingUserPrincipal? {
        return try {
            val claims: Claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            val userId = claims.subject
            val clinicId = claims[CLAIM_CLINIC_ID]?.toString()?.toLongOrNull()

            @Suppress("UNCHECKED_CAST")
            val roles = (claims[CLAIM_ROLES] as? List<String>) ?: emptyList()

            SchedulingUserPrincipal(
                userId = userId,
                clinicId = clinicId,
                roles = roles,
            )
        } catch (e: Exception) {
            log.warn(e) { "JWT 토큰 파싱 실패" }
            null
        }
    }
}
