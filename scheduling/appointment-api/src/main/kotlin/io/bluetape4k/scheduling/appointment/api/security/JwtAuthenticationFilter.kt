package io.bluetape4k.scheduling.appointment.api.security

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 인증 필터.
 *
 * Authorization 헤더에서 Bearer 토큰을 추출하여 검증하고,
 * SecurityContext에 인증 정보를 설정합니다.
 */
class JwtAuthenticationFilter(
    private val jwtTokenParser: JwtTokenParser,
) : OncePerRequestFilter() {

    companion object : KLogging() {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)
        if (token != null) {
            val principal = jwtTokenParser.parse(token)
            if (principal != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.authorities,
                )
                SecurityContextHolder.getContext().authentication = authentication
                log.debug { "JWT 인증 성공: userId=${principal.userId}, roles=${principal.roles}" }
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader(AUTHORIZATION_HEADER)
        return if (header != null && header.startsWith(BEARER_PREFIX)) {
            header.substring(BEARER_PREFIX.length)
        } else {
            null
        }
    }
}
