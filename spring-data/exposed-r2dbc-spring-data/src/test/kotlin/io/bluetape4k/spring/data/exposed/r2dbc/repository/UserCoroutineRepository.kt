package io.bluetape4k.spring.data.exposed.r2dbc.repository

import io.bluetape4k.spring.data.exposed.r2dbc.domain.User
import io.bluetape4k.spring.data.exposed.r2dbc.domain.Users
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow

/**
 * 테스트용 코루틴 기반 User Repository.
 * [CoroutineExposedRepository]의 기본 CRUD 메서드만 사용합니다.
 */
interface UserCoroutineRepository : CoroutineExposedRepository<Users, User, Long> {

    override fun toDomain(row: ResultRow): User =
        User(
            id = row[Users.id].value,
            name = row[Users.name],
            email = row[Users.email],
            age = row[Users.age],
        )

    override fun toPersistValues(domain: User): Map<Column<*>, Any?> =
        mapOf(
            Users.name to domain.name,
            Users.email to domain.email,
            Users.age to domain.age,
        )
}
