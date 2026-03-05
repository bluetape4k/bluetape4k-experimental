package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.UserEntity
import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.UserRepository
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional

@Transactional
class PartTreeExposedQueryTest : AbstractExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        Users.deleteAll()
    }

    private fun createUsers() {
        UserEntity.new { name = "Alice"; email = "alice@example.com"; age = 30 }
        UserEntity.new { name = "Bob"; email = "bob@example.com"; age = 25 }
        UserEntity.new { name = "Charlie"; email = "charlie@example.com"; age = 35 }
        UserEntity.new { name = "Alice"; email = "alice2@example.com"; age = 20 }
    }

    @Test
    fun `findByName returns matching entities`() {
        createUsers()
        val results = userRepository.findByName("Alice")
        results shouldHaveSize 2
        results.all { it.name == "Alice" }.shouldBeTrue()
    }

    @Test
    fun `findByAgeGreaterThan filters correctly`() {
        createUsers()
        val results = userRepository.findByAgeGreaterThan(25)
        results.all { it.age > 25 }.shouldBeTrue()
    }

    @Test
    fun `findByEmailContaining filters by substring`() {
        createUsers()
        val results = userRepository.findByEmailContaining("alice")
        results shouldHaveSize 2
    }

    @Test
    fun `findByNameAndAge returns single result`() {
        createUsers()
        val user = userRepository.findByNameAndAge("Alice", 30)
        user.shouldNotBeNull()
        user!!.email shouldBeEqualTo "alice@example.com"
    }

    @Test
    fun `countByAge returns correct count`() {
        createUsers()
        val count = userRepository.countByAge(30)
        count shouldBeEqualTo 1L
    }

    @Test
    fun `existsByEmail returns true when found`() {
        createUsers()
        userRepository.existsByEmail("alice@example.com").shouldBeTrue()
    }

    @Test
    fun `existsByEmail returns false when not found`() {
        createUsers()
        userRepository.existsByEmail("notexist@example.com").shouldBeFalse()
    }

    @Test
    fun `deleteByName removes matching entities`() {
        createUsers()
        val deleted = userRepository.deleteByName("Alice")
        deleted shouldBeEqualTo 2L
        userRepository.findByName("Alice").shouldBeEmpty()
    }

    @Test
    fun `findByAgeBetween returns entities in range`() {
        createUsers()
        val results = userRepository.findByAgeBetween(25, 35)
        results.all { it.age in 25..35 }.shouldBeTrue()
    }

    @Test
    fun `findTop3ByOrderByAgeDesc returns top 3 oldest`() {
        createUsers()
        val results = userRepository.findTop3ByOrderByAgeDesc()
        results shouldHaveSize 3
        (results[0].age >= results[1].age).shouldBeTrue()
    }

    @Test
    fun `findAll with Sort`() {
        createUsers()
        val results = userRepository.findAll(Sort.by(Sort.Direction.ASC, "age"))
        results.shouldNotBeEmpty()
        val ages = results.map { it.age }
        ages shouldBeEqualTo ages.sorted()
    }

    @Test
    fun `findAll with Pageable and Sort`() {
        createUsers()
        val page = userRepository.findAll(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "age")))
        page.content shouldHaveSize 2
        page.totalElements shouldBeEqualTo 4L
        (page.content[0].age >= page.content[1].age).shouldBeTrue()
    }
}
