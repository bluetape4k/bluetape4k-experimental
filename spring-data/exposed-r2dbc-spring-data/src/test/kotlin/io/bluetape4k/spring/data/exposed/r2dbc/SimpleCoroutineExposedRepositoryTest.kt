package io.bluetape4k.spring.data.exposed.r2dbc

import io.bluetape4k.spring.data.exposed.r2dbc.domain.UserEntity
import io.bluetape4k.spring.data.exposed.r2dbc.domain.Users
import io.bluetape4k.spring.data.exposed.r2dbc.repository.UserCoroutineRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class SimpleCoroutineExposedRepositoryTest : AbstractCoroutineExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserCoroutineRepository

    @AfterEach
    fun tearDown() {
        transaction { Users.deleteAll() }
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
    fun `findById returns entity`() = runTest {
        val user = createUser("Alice", "alice@example.com", 30)
        val found = userRepository.findByIdOrNull(user.id.value)
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("Alice")
    }

    @Test
    fun `findAll as Flow returns all entities`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val all = userRepository.findAll().toList()
        assertThat(all).hasSize(2)
    }

    @Test
    fun `findAllList returns all entities`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val all = userRepository.findAllList()
        assertThat(all).hasSize(2)
    }

    @Test
    fun `count returns correct total`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        assertThat(userRepository.count()).isEqualTo(2L)
    }

    @Test
    fun `existsById returns true when entity exists`() = runTest {
        val user = createUser("Alice", "alice@example.com", 30)
        assertThat(userRepository.existsById(user.id.value)).isTrue()
    }

    @Test
    fun `existsById returns false when entity does not exist`() = runTest {
        assertThat(userRepository.existsById(-1L)).isFalse()
    }

    @Test
    fun `deleteById removes entity`() = runTest {
        val user = createUser("Alice", "alice@example.com", 30)
        userRepository.deleteById(user.id.value)
        assertThat(userRepository.findByIdOrNull(user.id.value)).isNull()
    }

    @Test
    fun `deleteAll removes all entities`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        userRepository.deleteAll()
        assertThat(userRepository.count()).isEqualTo(0L)
    }

    @Test
    fun `findAll with Sort returns sorted list`() = runTest {
        createUser("Charlie", "charlie@example.com", 35)
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val results = userRepository.findAll(Sort.by(Sort.Direction.ASC, "age"))
        assertThat(results.map { it.age }).isSortedAccordingTo(Comparator.naturalOrder())
    }

    @Test
    fun `findAll with Pageable returns page`() = runTest {
        repeat(5) { i -> createUser("User$i", "user$i@example.com", 20 + i) }
        val page = userRepository.findAll(PageRequest.of(0, 3))
        assertThat(page.content).hasSize(3)
        assertThat(page.totalElements).isEqualTo(5L)
    }

    @Test
    fun `findAll with DSL op filters correctly`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 17)
        val adults = userRepository.findAll { Users.age greaterEq 18 }
        assertThat(adults).hasSize(1)
        assertThat(adults[0].name).isEqualTo("Alice")
    }

    @Test
    fun `count with DSL op returns correct count`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 17)
        assertThat(userRepository.count { Users.age greaterEq 18 }).isEqualTo(1L)
    }

    @Test
    fun `exists with DSL op returns true when found`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        assertThat(userRepository.exists { Users.name eq "Alice" }).isTrue()
        assertThat(userRepository.exists { Users.name eq "Nobody" }).isFalse()
    }
}
