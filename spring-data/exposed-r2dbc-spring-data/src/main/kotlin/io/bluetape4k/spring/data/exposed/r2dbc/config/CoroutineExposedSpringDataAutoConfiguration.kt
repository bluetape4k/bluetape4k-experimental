package io.bluetape4k.spring.data.exposed.r2dbc.config

import io.bluetape4k.spring.data.exposed.config.ExposedSpringDataAutoConfiguration
import io.bluetape4k.spring.data.exposed.mapping.ExposedMappingContext
import org.jetbrains.exposed.v1.dao.EntityClass
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 코루틴 기반 Spring Data Exposed 자동 설정입니다.
 * Phase 1의 [ExposedSpringDataAutoConfiguration] 이후에 실행됩니다.
 */
@AutoConfiguration(after = [ExposedSpringDataAutoConfiguration::class])
@ConditionalOnClass(EntityClass::class)
@Configuration(proxyBeanMethods = false)
class CoroutineExposedSpringDataAutoConfiguration {

    /**
     * Phase 1 AutoConfiguration에 의해 이미 등록된 경우 생략합니다.
     */
    @Bean
    @ConditionalOnMissingBean
    fun exposedMappingContext(): ExposedMappingContext = ExposedMappingContext()
}
