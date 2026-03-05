package io.bluetape4k.spring.data.exposed.repository

import io.bluetape4k.spring.data.exposed.annotation.Query
import io.bluetape4k.spring.data.exposed.domain.UserEntity

interface UserRepository : ExposedRepository<UserEntity, Long> {

    fun findByName(name: String): List<UserEntity>

    fun findByAgeGreaterThan(age: Int): List<UserEntity>

    fun findByEmailContaining(keyword: String): List<UserEntity>

    fun findByNameAndAge(name: String, age: Int): UserEntity?

    fun countByAge(age: Int): Long

    fun existsByEmail(email: String): Boolean

    fun deleteByName(name: String): Long

    fun findByAgeBetween(min: Int, max: Int): List<UserEntity>

    fun findByNameOrderByAgeDesc(name: String): List<UserEntity>

    fun findTop3ByOrderByAgeDesc(): List<UserEntity>

    @Query("SELECT * FROM users WHERE email = ?1")
    fun findByEmailNative(email: String): List<UserEntity>
}
