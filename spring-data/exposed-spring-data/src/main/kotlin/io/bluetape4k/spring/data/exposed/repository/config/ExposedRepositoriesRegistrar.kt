package io.bluetape4k.spring.data.exposed.repository.config

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension

/**
 * `@EnableExposedRepositories` 어노테이션으로 활성화되는 Repository 빈 등록기입니다.
 */
class ExposedRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport() {

    override fun getAnnotation(): Class<out Annotation> =
        EnableExposedRepositories::class.java

    override fun getExtension(): RepositoryConfigurationExtension =
        ExposedRepositoryConfigurationExtension()
}
