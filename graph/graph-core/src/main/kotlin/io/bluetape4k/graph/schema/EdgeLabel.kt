package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 그래프 간선(Edge) 스키마 정의.
 */
abstract class EdgeLabel(
    val label: String,
    val from: VertexLabel,
    val to: VertexLabel,
) {
    private val _properties = mutableListOf<PropertyDef<*>>()
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    fun string(name: String) = PropertyDef<String>(name, String::class).also { _properties.add(it) }
    fun integer(name: String) = PropertyDef<Int>(name, Int::class).also { _properties.add(it) }
    fun long(name: String) = PropertyDef<Long>(name, Long::class).also { _properties.add(it) }
    fun boolean(name: String) = PropertyDef<Boolean>(name, Boolean::class).also { _properties.add(it) }
    fun localDate(name: String) = PropertyDef<LocalDate>(name, LocalDate::class).also { _properties.add(it) }
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name, LocalDateTime::class).also { _properties.add(it) }
    fun <E : Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef<E>(name, type).also { _properties.add(it) }
}
