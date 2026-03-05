package io.bluetape4k.spring.data.exposed.r2dbc.config

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension

class CoroutineExposedRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport() {

    override fun getAnnotation(): Class<out Annotation> =
        EnableCoroutineExposedRepositories::class.java

    override fun getExtension(): RepositoryConfigurationExtension =
        CoroutineExposedRepositoryConfigurationExtension()
}
