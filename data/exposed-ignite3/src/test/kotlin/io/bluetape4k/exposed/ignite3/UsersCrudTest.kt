package io.bluetape4k.exposed.ignite3

import io.bluetape4k.exposed.ignite3.domain.UserDTO
import io.bluetape4k.exposed.ignite3.domain.Users
import io.bluetape4k.exposed.ignite3.domain.toUserDTO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersCrudTest : AbstractIgnite3Test() {

    companion object : KLogging()

    @BeforeAll
    fun setupTable() {
        dropAndCreateTables(Users)
    }

    @BeforeEach
    fun setup() {
        transaction(db) {
            Users.deleteAll()
            db.dialectMetadata.resetCaches()
        }
    }

    @Test
    fun `insert and select user`() {
        val dto = UserDTO(username = "debop", email = "debop@example.com", age = 40)

        val inserted = transaction(db) {
            Users.insert {
                it[username] = dto.username
                it[email] = dto.email
                it[age] = dto.age
            }.resultedValues!!.single().toUserDTO()
        }

        log.debug { "inserted: $inserted" }

        inserted.username shouldBeEqualTo dto.username
        inserted.email shouldBeEqualTo dto.email
        inserted.age shouldBeEqualTo dto.age
        inserted.id.shouldNotBeNull()
    }

    @Test
    fun `select all users`() {
        transaction(db) {
            repeat(3) { i ->
                Users.insert {
                    it[username] = "user$i"
                    it[email] = "user$i@example.com"
                    it[age] = 20 + i
                }
            }
        }

        val users = transaction(db) {
            Users.selectAll().map { it.toUserDTO() }
        }

        users shouldHaveSize 3
        users.forEach { log.debug { "user: $it" } }
    }

    @Test
    fun `update user`() {
        val id = transaction(db) {
            Users.insert {
                it[username] = "before"
                it[email] = "before@example.com"
            }.resultedValues!!.single()[Users.id].value
        }

        transaction(db) {
            Users.update({ Users.id eq id }) {
                it[username] = "after"
                it[email] = "after@example.com"
            }
        }

        val updated = transaction(db) {
            Users.selectAll().where { Users.id eq id }.single().toUserDTO()
        }

        updated.username shouldBeEqualTo "after"
        updated.email shouldBeEqualTo "after@example.com"
    }

    @Test
    fun `delete user`() {
        val id = transaction(db) {
            Users.insert {
                it[username] = "to-delete"
                it[email] = "delete@example.com"
            }.resultedValues!!.single()[Users.id].value
        }

        transaction(db) {
            Users.deleteWhere { Users.id eq id }
        }

        val deleted = transaction(db) {
            Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserDTO()
        }

        deleted.shouldBeNull()
    }

    @Test
    fun `count users`() {
        transaction(db) {
            repeat(5) { i ->
                Users.insert {
                    it[username] = "user$i"
                    it[email] = "user$i@example.com"
                }
            }
        }

        val count = transaction(db) {
            Users.selectAll().count()
        }

        count shouldBeEqualTo 5L
    }
}
