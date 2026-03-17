package io.bluetape4k.spring.data.exposed.jdbc.repository.support

import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.springframework.data.repository.core.support.AbstractEntityInformation
import kotlin.reflect.full.companionObjectInstance

/**
 * [ExposedEntityInformation]의 기본 구현체입니다.
 * Domain 클래스의 companion object에서 [EntityClass]를 추출합니다.
 */
@Suppress("UNCHECKED_CAST")
class ExposedEntityInformationImpl<E : Entity<ID>, ID : Any>(
    domainClass: Class<E>,
    override val entityClass: EntityClass<ID, E>,
) : AbstractEntityInformation<E, ID>(domainClass), ExposedEntityInformation<E, ID> {

    override val table: IdTable<ID> = entityClass.table

    override fun getId(entity: E): ID? =
        runCatching { entity.id.value }.getOrNull()

    override fun getIdType(): Class<ID> =
        resolveIdType(javaType) as Class<ID>

    override fun isNew(entity: E): Boolean =
        runCatching { entity.id.value; false }.getOrDefault(true)

    private fun resolveIdType(clazz: Class<*>): Class<*> {
        var current: Class<*>? = clazz
        while (current != null) {
            val genericSuper = current.genericSuperclass
            if (genericSuper is java.lang.reflect.ParameterizedType) {
                val rawType = genericSuper.rawType as? Class<*>
                if (rawType != null && Entity::class.java.isAssignableFrom(rawType)) {
                    val typeArg = genericSuper.actualTypeArguments.firstOrNull()
                    if (typeArg is Class<*>) return typeArg
                }
            }
            current = current.superclass
        }
        return Long::class.java
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        operator fun <E : Entity<ID>, ID : Any> invoke(domainClass: Class<E>): ExposedEntityInformationImpl<E, ID> {
            val companion = domainClass.kotlin.companionObjectInstance
                ?: error("${domainClass.name} must have a companion object (EntityClass<ID, E>)")
            val entityClass = companion as? EntityClass<ID, E>
                ?: error("Companion of ${domainClass.name} must be EntityClass<ID, E>")
            return ExposedEntityInformationImpl(domainClass, entityClass)
        }
    }
}
