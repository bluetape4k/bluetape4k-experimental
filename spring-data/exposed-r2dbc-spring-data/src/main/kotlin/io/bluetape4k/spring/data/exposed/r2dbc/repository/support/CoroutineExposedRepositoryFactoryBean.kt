package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import io.bluetape4k.spring.data.exposed.mapping.ExposedMappingContext
import io.bluetape4k.spring.data.exposed.r2dbc.repository.CoroutineExposedRepository
import org.jetbrains.exposed.v1.dao.Entity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.Repository
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport

/**
 * 코루틴 Exposed Repository를 생성하는 [TransactionalRepositoryFactoryBeanSupport] 구현체입니다.
 */
class CoroutineExposedRepositoryFactoryBean<T : Repository<E, ID>, E : Entity<ID>, ID : Any>(
    repositoryInterface: Class<out T>,
) : TransactionalRepositoryFactoryBeanSupport<T, E, ID>(repositoryInterface) {

    @Autowired
    private lateinit var mappingContext: ExposedMappingContext

    override fun doCreateRepositoryFactory(): RepositoryFactorySupport =
        CoroutineExposedRepositoryFactory(mappingContext)

    override fun afterPropertiesSet() {
        setTransactionManager("springTransactionManager")
        super.afterPropertiesSet()
    }
}
