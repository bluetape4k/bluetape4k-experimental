package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.UserEntity
import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.UserRepository
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@Transactional
class SimpleExposedRepositoryTest: AbstractExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        transaction {
            Users.deleteAll()
        }
    }

    private fun createUser(name: String, email: String, age: Int): UserEntity =
        UserEntity.new {
            this.name = name
            this.email = email
            this.age = age
        }


    @Test
    fun `save and findById`() {
        val user = createUser("Alice", "alice@example.com", 30)
        val found = userRepository.findById(user.id.value)
        assertThat(found).isPresent
        assertThat(found.get().name).isEqualTo("Alice")
    }

    @Test
    fun `findAll returns all entities`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val all = userRepository.findAll()
        assertThat(all).hasSize(2)
    }

    @Test
    fun `count returns total count`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val count = userRepository.count()
        assertThat(count).isEqualTo(2L)
    }

    @Test
    fun `existsById returns true when entity exists`() {
        val user = createUser("Alice", "alice@example.com", 30)
        val exists = userRepository.existsById(user.id.value)
        assertThat(exists).isTrue()
    }

    @Test
    fun `deleteById removes entity`() {
        val user = createUser("Alice", "alice@example.com", 30)
        transaction { userRepository.deleteById(user.id.value) }
        val found = userRepository.findById(user.id.value)
        assertThat(found).isEmpty
    }

    @Test
    fun `deleteAll removes all entities`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        userRepository.deleteAll()
        val count = userRepository.count()
        assertThat(count).isEqualTo(0L)
    }

    @Test
    fun `findAll with DSL op`() {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 17)

        val adults = userRepository.findAll { Users.age greaterEq 18 }

        adults shouldHaveSize 1
        adults[0].name shouldBeEqualTo "Alice"
    }

    @Test
    fun `exists with DSL op`() {
        createUser("Alice", "alice@example.com", 30)
        val exists = userRepository.exists { Users.name eq "Alice" }

        assertThat(exists).isTrue()
    }

    @Test
    fun `findAll with paging`() {
        repeat(10) { i -> createUser("User$i", "user$i@example.com", 20 + i) }
        val page = userRepository.findAll(PageRequest.of(0, 3))

        assertThat(page.content).hasSize(3)
        assertThat(page.totalElements).isEqualTo(10L)
    }
}
