package io.bluetape4k.spring.data.exposed.r2dbc

import io.bluetape4k.spring.data.exposed.r2dbc.domain.User
import io.bluetape4k.spring.data.exposed.r2dbc.domain.Users
import io.bluetape4k.spring.data.exposed.r2dbc.repository.UserCoroutineRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class SimpleCoroutineExposedRepositoryTest : AbstractCoroutineExposedRepositoryTest() {

    @Autowired
    private lateinit var userRepository: UserCoroutineRepository

    @AfterEach
    fun tearDown(): Unit = runBlocking {
        suspendTransaction { Users.deleteAll() }
    }

    private suspend fun createUser(name: String, email: String, age: Int): User =
        suspendTransaction {
            val id = Users.insertAndGetId {
                it[Users.name] = name
                it[Users.email] = email
                it[Users.age] = age
            }.value
            User(id = id, name = name, email = email, age = age)
        }

    private suspend fun <T> inTx(block: suspend () -> T): T =
        suspendTransaction { block() }

    @Test
    fun `findById returns entity`() = runTest {
        val user = createUser("Alice", "alice@example.com", 30)
        val userId = requireNotNull(user.id)
        val found = inTx { userRepository.findByIdOrNull(userId) }
        found.shouldNotBeNull()
        found.name shouldBeEqualTo "Alice"
    }

    @Test
    fun `findAll as Flow returns all entities`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val all = inTx { userRepository.findAll().toList() }
        all shouldHaveSize 2
    }

    @Test
    fun `findAllList returns all entities`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val all = inTx { userRepository.findAll().toList() }
        all shouldHaveSize 2
    }

    @Test
    fun `count returns correct total`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        inTx { userRepository.count() } shouldBeEqualTo 2L
    }

    @Test
    fun `existsById returns true when entity exists`() = runTest {
        val user = createUser("Alice", "alice@example.com", 30)
        inTx { userRepository.existsById(requireNotNull(user.id)) }.shouldBeTrue()
    }

    @Test
    fun `existsById returns false when entity does not exist`() = runTest {
        inTx { userRepository.existsById(-1L) }.shouldBeFalse()
    }

    @Test
    fun `deleteById removes entity`() = runTest {
        val user = createUser("Alice", "alice@example.com", 30)
        val userId = requireNotNull(user.id)
        inTx { userRepository.deleteById(userId) }
        inTx { userRepository.findByIdOrNull(userId) }.shouldBeNull()
    }

    @Test
    fun `deleteAll removes all entities`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        inTx { userRepository.deleteAll() }
        inTx { userRepository.count() } shouldBeEqualTo 0L
    }

    @Test
    fun `findAll with Sort returns sorted list`() = runTest {
        createUser("Charlie", "charlie@example.com", 35)
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 25)
        val results = inTx {
            userRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "age"))).content
        }
        val ages = results.map { it.age }
        ages shouldBeEqualTo ages.sorted()
    }

    @Test
    fun `findAll with Pageable returns page`() = runTest {
        repeat(5) { i -> createUser("User$i", "user$i@example.com", 20 + i) }
        val page = inTx { userRepository.findAll(PageRequest.of(0, 3)) }
        page.content shouldHaveSize 3
        page.totalElements shouldBeEqualTo 5L
    }

    @Test
    fun `count with DSL op filters correctly`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 17)
        inTx { userRepository.count { Users.age greaterEq 18 } } shouldBeEqualTo 1L
    }

    @Test
    fun `count with DSL op returns correct count`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        createUser("Bob", "bob@example.com", 17)
        inTx { userRepository.count { Users.age greaterEq 18 } } shouldBeEqualTo 1L
    }

    @Test
    fun `exists with DSL op returns true when found`() = runTest {
        createUser("Alice", "alice@example.com", 30)
        inTx { userRepository.exists { Users.name eq "Alice" } }.shouldBeTrue()
        inTx { userRepository.exists { Users.name eq "Nobody" } }.shouldBeFalse()
    }
}
