package io.bluetape4k.scheduling.appointment.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI / Swagger UI 설정.
 *
 * Swagger UI: http://localhost:8080/swagger-ui/index.html
 * API Docs:   http://localhost:8080/v3/api-docs
 */
@Configuration
class OpenApiConfig {

    companion object {
        private const val SECURITY_SCHEME_NAME = "bearerAuth"
    }

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Appointment Scheduling API")
                .description("병원 진료 예약 스케줄링 시스템 REST API")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("bluetape4k")
                        .url("https://github.com/bluetape4k"),
                ),
        )
        .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .components(
            Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    SecurityScheme()
                        .name(SECURITY_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("외부 인증 서비스에서 발급한 JWT 토큰"),
                ),
        )
}
