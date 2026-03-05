package io.bluetape4k.spring.data.exposed.r2dbc.config

import io.bluetape4k.spring.data.exposed.annotation.ExposedEntity
import io.bluetape4k.spring.data.exposed.r2dbc.repository.CoroutineExposedRepository
import io.bluetape4k.spring.data.exposed.r2dbc.repository.support.CoroutineExposedRepositoryFactoryBean
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport
import org.springframework.data.repository.core.RepositoryMetadata

class CoroutineExposedRepositoryConfigurationExtension : RepositoryConfigurationExtensionSupport() {

    override fun getModuleName(): String = "COROUTINE_EXPOSED"

    override fun getModulePrefix(): String = "coroutineExposed"

    override fun getRepositoryFactoryBeanClassName(): String =
        CoroutineExposedRepositoryFactoryBean::class.java.name

    override fun getIdentifyingAnnotations(): Collection<Class<out Annotation>> =
        listOf(ExposedEntity::class.java)

    override fun getIdentifyingTypes(): Collection<Class<*>> =
        listOf(CoroutineExposedRepository::class.java)

    /**
     * 코루틴/Flow 기반의 reactive repository를 지원합니다.
     * Spring Data의 reactive 체크를 우회하여 suspend/Flow 메서드를 포함한 모든 repository를 허용합니다.
     */
    override fun useRepositoryConfiguration(metadata: RepositoryMetadata): Boolean = true
}
