package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.UserEntity
import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class QueryByExampleTest : AbstractExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        transaction { Users.deleteAll() }
    }

    @Test
    fun `findAll with DSL op for age 30`() {
        transaction {
            UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
            UserEntity.new { name = "Bob"; email = "bob@example.com"; age = 30 }
            UserEntity.new { name = "Charlie"; email = "charlie@example.com"; age = 25 }
        }

        val results = transaction {
            userRepository.findAll { Users.age eq 30 }
        }
        assertThat(results).hasSize(2)
    }

    @Test
    fun `count with DSL op`() {
        transaction {
            UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
            UserEntity.new { name = "Bob"; email = "bob@example.com"; age = 25 }
        }
        val count = transaction {
            userRepository.count { Users.age eq 30 }
        }
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `exists with DSL op`() {
        transaction {
            UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
        }
        assertThat(transaction { userRepository.exists { Users.name eq "Alice" } }).isTrue()
        assertThat(transaction { userRepository.exists { Users.name eq "Nobody" } }).isFalse()
    }
}
