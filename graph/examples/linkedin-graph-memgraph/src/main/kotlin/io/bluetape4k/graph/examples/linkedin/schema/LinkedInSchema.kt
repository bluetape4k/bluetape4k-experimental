package io.bluetape4k.graph.examples.linkedin.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

// 정점 라벨
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val title = string("title")
    val company = string("company")
    val location = string("location")
    val skills = stringList("skills")
    val connectionCount = integer("connectionCount")
}

object CompanyLabel : VertexLabel("Company") {
    val name = string("name")
    val industry = string("industry")
    val size = string("size")   // "startup", "small", "medium", "large", "enterprise"
    val location = string("location")
}

object SkillLabel : VertexLabel("Skill") {
    val name = string("name")
    val category = string("category")   // "programming", "management", "design", etc.
}

// 간선 라벨
object KnowsLabel : EdgeLabel("KNOWS", PersonLabel, PersonLabel) {
    val since = string("since")         // ISO date string
    val strength = integer("strength")  // 1-10
}

object WorksAtLabel : EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
    val role = string("role")
    val startDate = string("startDate")
    val isCurrent = boolean("isCurrent")
}

object FollowsLabel : EdgeLabel("FOLLOWS", PersonLabel, PersonLabel)

object HasSkillLabel : EdgeLabel("HAS_SKILL", PersonLabel, SkillLabel) {
    val level = string("level")  // "beginner", "intermediate", "expert"
}

object EndorsesLabel : EdgeLabel("ENDORSES", PersonLabel, PersonLabel) {
    val skillName = string("skillName")
}
