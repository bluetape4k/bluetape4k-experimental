package io.bluetape4k.spring.data.exposed.repository.config

import io.bluetape4k.spring.data.exposed.annotation.ExposedEntity
import io.bluetape4k.spring.data.exposed.repository.ExposedRepository
import io.bluetape4k.spring.data.exposed.repository.support.ExposedRepositoryFactoryBean
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport

/**
 * Exposed Spring Data 모듈 설정 확장입니다.
 */
class ExposedRepositoryConfigurationExtension : RepositoryConfigurationExtensionSupport() {

    override fun getModuleName(): String = "EXPOSED"

    @Deprecated("use getModuleName instead.")
    override fun getModulePrefix(): String = "exposed"

    override fun getRepositoryFactoryBeanClassName(): String =
        ExposedRepositoryFactoryBean::class.java.name

    override fun getIdentifyingAnnotations(): Collection<Class<out Annotation>> =
        listOf(ExposedEntity::class.java)

    override fun getIdentifyingTypes(): Collection<Class<*>> =
        listOf(ExposedRepository::class.java)
}
