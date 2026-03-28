package io.bluetape4k.spring.data.exposed.r2dbc.repository.config

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension

/**
 * `@EnableExposedSuspendRepositories` 어노테이션으로 활성화되는 suspend Repository 빈 등록기입니다.
 */
class ExposedSuspendRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport() {

    override fun getAnnotation(): Class<out Annotation> =
        EnableExposedSuspendRepositories::class.java

    override fun getExtension(): RepositoryConfigurationExtension =
        ExposedSuspendRepositoryConfigurationExtension()
}
