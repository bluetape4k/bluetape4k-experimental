package io.bluetape4k.spring.data.exposed.config

import io.bluetape4k.spring.data.exposed.mapping.ExposedMappingContext
import org.jetbrains.exposed.v1.dao.EntityClass
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Data Exposed 자동 설정입니다.
 * Exposed `EntityClass`가 classpath에 있을 때 활성화됩니다.
 */
@AutoConfiguration
@ConditionalOnClass(EntityClass::class)
@Configuration(proxyBeanMethods = false)
class ExposedSpringDataAutoConfiguration {

    @Bean
    fun exposedMappingContext(): ExposedMappingContext = ExposedMappingContext()
}
