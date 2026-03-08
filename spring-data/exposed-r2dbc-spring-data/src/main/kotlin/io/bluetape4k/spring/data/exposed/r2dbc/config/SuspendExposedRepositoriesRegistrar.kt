package io.bluetape4k.spring.data.exposed.r2dbc.config

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension

class SuspendExposedRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport() {

    override fun getAnnotation(): Class<out Annotation> =
        EnableSuspendExposedRepositories::class.java

    override fun getExtension(): RepositoryConfigurationExtension =
        SuspendExposedRepositoryConfigurationExtension()
}
