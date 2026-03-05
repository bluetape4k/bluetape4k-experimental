package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import io.bluetape4k.spring.data.exposed.mapping.ExposedMappingContext
import io.bluetape4k.spring.data.exposed.repository.support.ExposedEntityInformation
import io.bluetape4k.spring.data.exposed.repository.support.ExposedEntityInformationImpl
import org.jetbrains.exposed.v1.dao.Entity
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.RepositoryFactorySupport

/**
 * [CoroutineExposedRepositoryFactory]는 코루틴 기반 Exposed Repository 프록시를 생성합니다.
 */
@Suppress("UNCHECKED_CAST")
class CoroutineExposedRepositoryFactory(
    private val mappingContext: ExposedMappingContext,
) : RepositoryFactorySupport() {

    override fun <T : Any, ID : Any> getEntityInformation(domainClass: Class<T>): EntityInformation<T, ID> =
        ExposedEntityInformationImpl.of(domainClass as Class<Entity<Any>>) as EntityInformation<T, ID>

    override fun getTargetRepository(information: RepositoryInformation): Any {
        val entityInfo = getEntityInformation<Entity<Any>, Any>(
            information.domainType as Class<Entity<Any>>
        ) as ExposedEntityInformation<Entity<Any>, Any>
        return SimpleCoroutineExposedRepository(entityInfo)
    }

    override fun getRepositoryBaseClass(metadata: RepositoryMetadata): Class<*> =
        SimpleCoroutineExposedRepository::class.java
}
