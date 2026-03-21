package io.bluetape4k.scheduling.appointment.api.security

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test

/**
 * [JwtTokenParser] 테스트.
 */
class JwtTokenParserTest {

    private val parser = JwtTokenParser(
        JwtSecurityProperties(
            enabled = true,
            secret = TestJwtProvider.secret,
            issuer = TestJwtProvider.issuer,
        ),
    )

    @Test
    fun `유효한 토큰 파싱 - 사용자 정보 추출`() {
        val token = TestJwtProvider.createToken(
            userId = "user-123",
            clinicId = 5L,
            roles = listOf(SchedulingRole.ADMIN, SchedulingRole.STAFF),
        )

        val principal = parser.parse(token)

        principal.shouldNotBeNull()
        principal.userId.shouldBeEqualTo("user-123")
        principal.clinicId.shouldBeEqualTo(5L)
        principal.roles.shouldContain(SchedulingRole.ADMIN)
        principal.roles.shouldContain(SchedulingRole.STAFF)
    }

    @Test
    fun `만료된 토큰 - null 반환`() {
        val token = TestJwtProvider.expiredToken()

        val principal = parser.parse(token)

        principal.shouldBeNull()
    }

    @Test
    fun `잘못된 토큰 - null 반환`() {
        val principal = parser.parse("invalid-token-string")

        principal.shouldBeNull()
    }

    @Test
    fun `clinicId 없는 토큰 - clinicId null`() {
        val token = TestJwtProvider.createToken(clinicId = null)

        val principal = parser.parse(token)

        principal.shouldNotBeNull()
        principal.clinicId.shouldBeNull()
    }

    @Test
    fun `authorities에 ROLE_ prefix 포함`() {
        val token = TestJwtProvider.adminToken()

        val principal = parser.parse(token)

        principal.shouldNotBeNull()
        val authorityNames = principal.authorities.map { it.authority }
        authorityNames.shouldContain("ROLE_ADMIN")
    }
}
