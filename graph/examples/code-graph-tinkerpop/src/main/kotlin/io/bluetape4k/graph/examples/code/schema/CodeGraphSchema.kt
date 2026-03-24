package io.bluetape4k.graph.examples.code.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

// 정점 라벨: 코드 요소

object ModuleLabel : VertexLabel("Module") {
    val name = string("name")
    val path = string("path")         // 파일시스템 경로
    val version = string("version")
    val language = string("language") // "kotlin", "java", etc.
}

object ClassLabel : VertexLabel("Class") {
    val name = string("name")
    val qualifiedName = string("qualifiedName")
    val module = string("module")
    val isAbstract = boolean("isAbstract")
    val isInterface = boolean("isInterface")
}

object FunctionLabel : VertexLabel("Function") {
    val name = string("name")
    val signature = string("signature")
    val className = string("className")
    val module = string("module")
    val lineCount = integer("lineCount")
}

// 간선 라벨: 코드 관계

object DependsOnLabel : EdgeLabel("DEPENDS_ON", ModuleLabel, ModuleLabel) {
    val dependencyType = string("dependencyType")  // "compile", "runtime", "test"
    val version = string("version")
}

object ImportsLabel : EdgeLabel("IMPORTS", ClassLabel, ClassLabel)

object ExtendsLabel : EdgeLabel("EXTENDS", ClassLabel, ClassLabel)

object ImplementsLabel : EdgeLabel("IMPLEMENTS", ClassLabel, ClassLabel)

object CallsLabel : EdgeLabel("CALLS", FunctionLabel, FunctionLabel) {
    val callCount = integer("callCount")   // 호출 횟수
    val isRecursive = boolean("isRecursive")
}

object BelongsToLabel : EdgeLabel("BELONGS_TO", ClassLabel, ModuleLabel)  // Class/Function → Module
