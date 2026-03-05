package io.bluetape4k.spring.data.exposed.repository.support

import io.bluetape4k.spring.data.exposed.mapping.ExposedMappingContext
import io.bluetape4k.spring.data.exposed.repository.query.ExposedQueryLookupStrategy
import org.jetbrains.exposed.v1.dao.Entity
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.query.QueryLookupStrategy
import org.springframework.data.repository.query.ValueExpressionDelegate
import java.util.*

/**
 * Exposed Repository 인스턴스를 생성하는 Factory입니다.
 */
@Suppress("UNCHECKED_CAST")
class ExposedRepositoryFactory(
    private val mappingContext: ExposedMappingContext,
) : RepositoryFactorySupport() {

    @Deprecated("Spring 4.0부터 Deprecated 됩니다.")
    override fun <T : Any, ID : Any> getEntityInformation(domainClass: Class<T>): EntityInformation<T, ID> =
        ExposedEntityInformationImpl.of(domainClass as Class<Entity<Any>>) as EntityInformation<T, ID>

    override fun getTargetRepository(information: RepositoryInformation): Any {
        val entityInfo = getEntityInformation<Entity<Any>, Any>(
            information.domainType as Class<Entity<Any>>
        ) as ExposedEntityInformation<Entity<Any>, Any>
        return SimpleExposedRepository(entityInfo)
    }

    override fun getRepositoryBaseClass(metadata: RepositoryMetadata): Class<*> =
        SimpleExposedRepository::class.java

    override fun getQueryLookupStrategy(
        key: QueryLookupStrategy.Key?,
        valueExpressionDelegate: ValueExpressionDelegate,
    ): Optional<QueryLookupStrategy> =
        Optional.of(
            ExposedQueryLookupStrategy.create(
                key ?: QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND
            )
        )
}
