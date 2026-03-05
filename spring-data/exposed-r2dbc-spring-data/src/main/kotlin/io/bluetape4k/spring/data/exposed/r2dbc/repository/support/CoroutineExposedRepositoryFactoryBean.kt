package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import org.springframework.data.repository.Repository
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport

/**
 * 코루틴 Exposed Repository를 생성하는 [TransactionalRepositoryFactoryBeanSupport] 구현체입니다.
 */
class CoroutineExposedRepositoryFactoryBean<T : Repository<E, ID>, E : Any, ID : Any>(
    repositoryInterface: Class<out T>,
) : TransactionalRepositoryFactoryBeanSupport<T, E, ID>(repositoryInterface) {

    override fun doCreateRepositoryFactory(): RepositoryFactorySupport =
        CoroutineExposedRepositoryFactory()

    override fun afterPropertiesSet() {
        setTransactionManager("springTransactionManager")
        super.afterPropertiesSet()
    }
}
