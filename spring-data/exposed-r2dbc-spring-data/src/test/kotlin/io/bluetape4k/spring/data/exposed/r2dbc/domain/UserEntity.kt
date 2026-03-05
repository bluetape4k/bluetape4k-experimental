package io.bluetape4k.spring.data.exposed.r2dbc.domain

import io.bluetape4k.spring.data.exposed.annotation.ExposedEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object Users : LongIdTable("coroutine_users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val age = integer("age")
}

@ExposedEntity
class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserEntity>(Users)

    var name: String by Users.name
    var email: String by Users.email
    var age: Int by Users.age
}
