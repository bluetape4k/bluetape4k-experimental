package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.UserEntity
import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class PartTreeExposedQueryTest : AbstractExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        transaction { Users.deleteAll() }
    }

    private fun createUsers() {
        transaction {
            UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
            UserEntity.new { name = "Bob"; email = "bob@example.com"; age = 25 }
            UserEntity.new { name = "Charlie"; email = "charlie@example.com"; age = 35 }
            UserEntity.new { name = "Alice"; email = "alice2@example.com"; age = 20 }
        }
    }

    @Test
    fun `findByName returns matching entities`() {
        createUsers()
        val results = transaction { userRepository.findByName("Alice") }
        assertThat(results).hasSize(2)
        assertThat(results.map { it.name }).allMatch { it == "Alice" }
    }

    @Test
    fun `findByAgeGreaterThan filters correctly`() {
        createUsers()
        val results = transaction { userRepository.findByAgeGreaterThan(25) }
        assertThat(results.map { it.age }).allMatch { it > 25 }
    }

    @Test
    fun `findByEmailContaining filters by substring`() {
        createUsers()
        val results = transaction { userRepository.findByEmailContaining("alice") }
        assertThat(results).hasSize(2)
    }

    @Test
    fun `findByNameAndAge returns single result`() {
        createUsers()
        val user = transaction { userRepository.findByNameAndAge("Alice", 30) }
        assertThat(user).isNotNull
        assertThat(user!!.email).isEqualTo("alice@example.com")
    }

    @Test
    fun `countByAge returns correct count`() {
        createUsers()
        val count = transaction { userRepository.countByAge(30) }
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `existsByEmail returns true when found`() {
        createUsers()
        val exists = transaction { userRepository.existsByEmail("alice@example.com") }
        assertThat(exists).isTrue()
    }

    @Test
    fun `existsByEmail returns false when not found`() {
        createUsers()
        val exists = transaction { userRepository.existsByEmail("notexist@example.com") }
        assertThat(exists).isFalse()
    }

    @Test
    fun `deleteByName removes matching entities`() {
        createUsers()
        val deleted = transaction { userRepository.deleteByName("Alice") }
        assertThat(deleted).isEqualTo(2L)
        val remaining = transaction { userRepository.findByName("Alice") }
        assertThat(remaining).isEmpty()
    }

    @Test
    fun `findByAgeBetween returns entities in range`() {
        createUsers()
        val results = transaction { userRepository.findByAgeBetween(25, 35) }
        assertThat(results.map { it.age }).allMatch { it in 25..35 }
    }

    @Test
    fun `findTop3ByOrderByAgeDesc returns top 3 oldest`() {
        createUsers()
        val results = transaction { userRepository.findTop3ByOrderByAgeDesc() }
        assertThat(results).hasSize(3)
        assertThat(results[0].age).isGreaterThanOrEqualTo(results[1].age)
    }

    @Test
    fun `findAll with Sort`() {
        createUsers()
        val results = transaction {
            userRepository.findAll(Sort.by(Sort.Direction.ASC, "age"))
        }
        assertThat(results).isNotEmpty
        val ages = results.map { it.age }
        assertThat(ages).isSortedAccordingTo(Comparator.naturalOrder())
    }

    @Test
    fun `findAll with Pageable and Sort`() {
        createUsers()
        val page = transaction {
            userRepository.findAll(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "age")))
        }
        assertThat(page.content).hasSize(2)
        assertThat(page.totalElements).isEqualTo(4L)
        assertThat(page.content[0].age).isGreaterThanOrEqualTo(page.content[1].age)
    }
}
