package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.UserEntity
import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

class SimpleExposedRepositoryTest : AbstractExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        transaction {
            Users.deleteAll()
        }
    }

    private fun createUser(name: String, email: String, age: Int): UserEntity =
        transaction {
            UserEntity.new {
                this.name = name
                this.email = email
                this.age = age
            }
        }

    @Test
    fun `save and findById`() {
        val user = createUser("Alice", "alice@example.com", 30)
        val found = transaction { userRepository.findById(user.id.value) }
        assertThat(found).isPresent
        assertThat(found.get().name).isEqualTo("Alice")
    }

    @Test
    fun `findAll returns all entities`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val all = transaction { userRepository.findAll() }
        assertThat(all).hasSize(2)
    }

    @Test
    fun `count returns total count`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val count = transaction { userRepository.count() }
        assertThat(count).isEqualTo(2L)
    }

    @Test
    fun `existsById returns true when entity exists`() {
        val user = createUser("Alice", "alice@example.com", 30)
        val exists = transaction { userRepository.existsById(user.id.value) }
        assertThat(exists).isTrue()
    }

    @Test
    fun `deleteById removes entity`() {
        val user = createUser("Alice", "alice@example.com", 30)
        transaction { userRepository.deleteById(user.id.value) }
        val found = transaction { userRepository.findById(user.id.value) }
        assertThat(found).isEmpty
    }

    @Test
    fun `deleteAll removes all entities`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        transaction { userRepository.deleteAll() }
        val count = transaction { userRepository.count() }
        assertThat(count).isEqualTo(0L)
    }

    @Test
    fun `findAll with DSL op`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 17)
        val adults = transaction {
            userRepository.findAll { Users.age greaterEq 18 }
        }
        assertThat(adults).hasSize(1)
        assertThat(adults[0].name).isEqualTo("Alice")
    }

    @Test
    fun `exists with DSL op`() {
        createUser("Alice", "alice@example.com", 30)
        val exists = transaction {
            userRepository.exists { Users.name eq "Alice" }
        }
        assertThat(exists).isTrue()
    }

    @Test
    fun `findAll with paging`() {
        repeat(10) { i -> createUser("User$i", "user$i@example.com", 20 + i) }
        val page = transaction {
            userRepository.findAll(PageRequest.of(0, 3))
        }
        assertThat(page.content).hasSize(3)
        assertThat(page.totalElements).isEqualTo(10L)
    }
}
