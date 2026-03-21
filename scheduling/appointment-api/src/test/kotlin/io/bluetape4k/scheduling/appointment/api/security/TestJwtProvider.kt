package io.bluetape4k.scheduling.appointment.api.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Base64
import java.util.Date

/**
 * 테스트용 JWT 토큰 생성 유틸리티.
 */
object TestJwtProvider {

    private const val TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1hcHBvaW50bWVudC1zY2hlZHVsaW5nLXN5c3RlbS0yNTY="
    private const val TEST_ISSUER = "appointment-auth-service"

    private val signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET))

    val secret: String = TEST_SECRET
    val issuer: String = TEST_ISSUER

    fun createToken(
        userId: String = "test-user",
        clinicId: Long? = 1L,
        roles: List<String> = listOf(SchedulingRole.ADMIN),
        expirationMs: Long = 3600000,
    ): String {
        val now = Date()
        val builder = Jwts.builder()
            .subject(userId)
            .issuer(TEST_ISSUER)
            .issuedAt(now)
            .expiration(Date(now.time + expirationMs))
            .claim("roles", roles)

        if (clinicId != null) {
            builder.claim("clinicId", clinicId)
        }

        return builder.signWith(signingKey).compact()
    }

    fun adminToken(clinicId: Long? = 1L): String =
        createToken(userId = "admin-user", clinicId = clinicId, roles = listOf(SchedulingRole.ADMIN))

    fun staffToken(clinicId: Long = 1L): String =
        createToken(userId = "staff-user", clinicId = clinicId, roles = listOf(SchedulingRole.STAFF))

    fun doctorToken(clinicId: Long = 1L): String =
        createToken(userId = "doctor-user", clinicId = clinicId, roles = listOf(SchedulingRole.DOCTOR))

    fun patientToken(): String =
        createToken(userId = "patient-user", clinicId = null, roles = listOf(SchedulingRole.PATIENT))

    fun expiredToken(): String =
        createToken(expirationMs = -1000)
}
