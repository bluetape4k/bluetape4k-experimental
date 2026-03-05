package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.UserEntity
import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.UserRepository
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class QueryByExampleTest : AbstractExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        Users.deleteAll()
    }

    @Test
    fun `findAll with DSL op for age 30`() {
        UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
        UserEntity.new { name = "Bob"; email = "bob@example.com"; age = 30 }
        UserEntity.new { name = "Charlie"; email = "charlie@example.com"; age = 25 }

        val results = userRepository.findAll { Users.age eq 30 }
        results shouldHaveSize 2
    }

    @Test
    fun `count with DSL op`() {
        UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
        UserEntity.new { name = "Bob"; email = "bob@example.com"; age = 25 }

        val count = userRepository.count { Users.age eq 30 }
        count shouldBeEqualTo 1L
    }

    @Test
    fun `exists with DSL op`() {
        UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }

        userRepository.exists { Users.name eq "Alice" }.shouldBeTrue()
        userRepository.exists { Users.name eq "Nobody" }.shouldBeFalse()
    }
}
