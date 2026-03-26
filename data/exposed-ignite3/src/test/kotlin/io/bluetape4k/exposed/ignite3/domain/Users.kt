package io.bluetape4k.exposed.ignite3.domain

import io.bluetape4k.exposed.core.dao.id.TimebasedUUIDTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.util.*

/**
 * Apache Ignite 3 테스트용 Users 테이블
 *
 * TimebasedUUIDTable을 사용하므로 UUID v7 을 기본키로 사용합니다.
 * Ignite 3는 UUID 네이티브 타입을 지원하므로 별도 변환이 불필요합니다.
 */
object Users : TimebasedUUIDTable("users") {
    val username = varchar("username", 100)
    val email = varchar("email", 255)
    val age = integer("age").nullable()
}

data class UserDTO(
    val id: UUID? = null,
    val username: String,
    val email: String,
    val age: Int? = null,
)

fun ResultRow.toUserDTO() = UserDTO(
    id = this[Users.id].value,
    username = this[Users.username],
    email = this[Users.email],
    age = this[Users.age],
)
