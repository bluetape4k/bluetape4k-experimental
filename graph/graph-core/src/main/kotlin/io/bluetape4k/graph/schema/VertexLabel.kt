package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 그래프 정점(Vertex) 스키마 정의. Exposed Table 스타일의 DSL.
 * 백엔드(AGE, Neo4j)에 무관하게 사용 가능.
 */
abstract class VertexLabel(val label: String) {
    private val _properties = mutableListOf<PropertyDef<*>>()
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    fun string(name: String) = PropertyDef<String>(name, String::class).also { _properties.add(it) }
    fun integer(name: String) = PropertyDef<Int>(name, Int::class).also { _properties.add(it) }
    fun long(name: String) = PropertyDef<Long>(name, Long::class).also { _properties.add(it) }
    fun boolean(name: String) = PropertyDef<Boolean>(name, Boolean::class).also { _properties.add(it) }
    @Suppress("UNCHECKED_CAST")
    fun stringList(name: String) = PropertyDef<List<String>>(name, List::class as KClass<out List<String>>).also { _properties.add(it) }
    @Suppress("UNCHECKED_CAST")
    fun json(name: String) = PropertyDef<Map<String, Any?>>(name, Map::class as KClass<out Map<String, Any?>>).also { _properties.add(it) }
    fun localDate(name: String) = PropertyDef<LocalDate>(name, LocalDate::class).also { _properties.add(it) }
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name, LocalDateTime::class).also { _properties.add(it) }
    fun <E : Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef<E>(name, type).also { _properties.add(it) }
}
