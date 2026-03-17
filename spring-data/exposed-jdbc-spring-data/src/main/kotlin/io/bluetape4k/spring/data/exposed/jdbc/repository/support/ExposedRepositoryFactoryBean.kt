package io.bluetape4k.spring.data.exposed.jdbc.repository.support

import io.bluetape4k.spring.data.exposed.jdbc.mapping.ExposedMappingContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.Repository
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport

/**
 * Exposed Repository Bean을 생성하는 FactoryBean입니다.
 * Spring의 트랜잭션 관리와 통합됩니다.
 */
class ExposedRepositoryFactoryBean<T : Repository<E, ID>, E : Any, ID : Any>(
    repositoryInterface: Class<out T>,
) : TransactionalRepositoryFactoryBeanSupport<T, E, ID>(repositoryInterface) {

    @Autowired
    private lateinit var mappingContext: ExposedMappingContext

    override fun doCreateRepositoryFactory(): RepositoryFactorySupport =
        ExposedRepositoryFactory(mappingContext)
}
