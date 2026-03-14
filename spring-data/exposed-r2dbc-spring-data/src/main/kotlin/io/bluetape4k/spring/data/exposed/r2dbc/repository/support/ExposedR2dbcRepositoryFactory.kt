package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import io.bluetape4k.exposed.core.HasIdentifier
import io.bluetape4k.spring.data.exposed.r2dbc.repository.SuspendExposedCrudRepository
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.core.ResolvableType
import org.springframework.data.repository.core.EntityInformation
import org.springframework.data.repository.core.RepositoryInformation
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.AbstractEntityInformation
import org.springframework.data.repository.core.support.RepositoryComposition
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.data.repository.query.QueryLookupStrategy
import org.springframework.data.repository.query.ValueExpressionDelegate
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Optional

/**
 * [ExposedR2dbcRepositoryFactory]는 테이블 기반 코루틴 Exposed Repository 프록시를 생성합니다.
 */
@Suppress("UNCHECKED_CAST")
class ExposedR2dbcRepositoryFactory : RepositoryFactorySupport() {

    override fun getEntityInformation(metadata: RepositoryMetadata): EntityInformation<*, *> =
        StaticEntityInformation(metadata.domainType as Class<Any>, metadata.idType as Class<Any>)

    override fun getTargetRepository(information: RepositoryInformation): Any {
        return createRepositoryImplementation(information.repositoryInterface)
    }

    override fun getRepositoryBaseClass(metadata: RepositoryMetadata): Class<*> =
        SimpleExposedR2dbcRepository::class.java

    override fun getRepositoryFragments(metadata: RepositoryMetadata): RepositoryComposition.RepositoryFragments =
        RepositoryComposition.RepositoryFragments.just(createRepositoryImplementation(metadata.repositoryInterface))

    override fun getQueryLookupStrategy(
        key: QueryLookupStrategy.Key?,
        valueExpressionDelegate: ValueExpressionDelegate,
    ): Optional<QueryLookupStrategy> = Optional.empty()

    private fun createRepositoryImplementation(repositoryInterface: Class<*>): SimpleExposedR2dbcRepository<HasIdentifier<Any>, Any> {
        val repositoryType = ResolvableType
            .forClass(repositoryInterface)
            .`as`(SuspendExposedCrudRepository::class.java)

        val tableType = repositoryType.getGeneric(0).resolve()
            ?: error("Cannot resolve table type for ${repositoryInterface.name}")

        val table = resolveTableInstance(tableType) as IdTable<Any>
        val mapper = resolveMapper(repositoryInterface)

        return SimpleExposedR2dbcRepository(
            table = table,
            toDomainMapper = mapper.toDomain,
            persistValuesProvider = mapper.toPersistValues,
        )
    }

    private fun resolveTableInstance(tableType: Class<*>): IdTable<*> {
        val objectInstance = tableType.kotlin.objectInstance
        if (objectInstance is IdTable<*>) return objectInstance

        val staticInstance = runCatching {
            val field = tableType.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.get(null)
        }.getOrNull()

        return staticInstance as? IdTable<*>
            ?: error("${tableType.name} must be an object that extends IdTable")
    }

    private fun resolveMapper(repositoryInterface: Class<*>): RepositoryMapper {
        val toDomainMethod = repositoryInterface.methods.firstOrNull {
            it.name == "toDomain" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == ResultRow::class.java
        } ?: error("${repositoryInterface.name} must override toDomain(ResultRow)")

        val toPersistValuesMethod = repositoryInterface.methods.firstOrNull {
            it.name == "toPersistValues" && it.parameterCount == 1
        } ?: error("${repositoryInterface.name} must override toPersistValues(domain)")

        val defaultMethodProxy = createDefaultMethodProxy(repositoryInterface)
        val toDomainHandle = bindDefaultMethodHandle(repositoryInterface, toDomainMethod, defaultMethodProxy)
        val toPersistValuesHandle = bindDefaultMethodHandle(repositoryInterface, toPersistValuesMethod, defaultMethodProxy)

        return RepositoryMapper(
            toDomain = { row -> toDomainHandle.invoke(row) as HasIdentifier<Any> },
            toPersistValues = { domain ->
                toPersistValuesHandle.invoke(domain) as Map<Column<*>, Any?>
            },
        )
    }

    private fun bindDefaultMethodHandle(
        repositoryInterface: Class<*>,
        method: Method,
        proxy: Any,
    ): MethodHandle {
        val lookup = MethodHandles.privateLookupIn(repositoryInterface, MethodHandles.lookup())
        return lookup.unreflectSpecial(method, repositoryInterface).bindTo(proxy)
    }

    private fun createDefaultMethodProxy(repositoryInterface: Class<*>): Any {
        val handler = InvocationHandler { proxy, method, args ->
            when {
                method.declaringClass == Any::class.java ->
                    when (method.name) {
                        "toString" -> "DefaultMethodProxy(${repositoryInterface.name})"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> null
                    }

                method.isDefault ->
                    InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))

                else -> error("Method '${method.name}' must provide a default implementation in ${repositoryInterface.name}")
            }
        }

        return Proxy.newProxyInstance(
            repositoryInterface.classLoader,
            arrayOf(repositoryInterface),
            handler,
        )
    }

    private data class RepositoryMapper(
        val toDomain: (ResultRow) -> HasIdentifier<Any>,
        val toPersistValues: (Any) -> Map<Column<*>, Any?>,
    )

    private class StaticEntityInformation<T : Any, ID : Any>(
        domainClass: Class<T>,
        private val idType: Class<ID>,
    ) : AbstractEntityInformation<T, ID>(domainClass) {

        override fun getId(entity: T): ID? = null

        override fun getIdType(): Class<ID> = idType

        override fun isNew(entity: T): Boolean = true
    }
}
