package io.bluetape4k.exposed.cockroachdb.crud

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.domain.Users
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

class CrudTest : AbstractCockroachDBTest() {

    companion object : KLogging()

    @Test
    fun `insert and select by id`() {
        withTables(Users) {
            val id = Users.insertAndGetId {
                it[username] = "alice"
                it[email] = "alice@example.com"
                it[age] = 30
            }

            val row = Users.selectAll().where { Users.id eq id }.singleOrNull()
            row.shouldNotBeNull()
            row[Users.username].shouldBeEqualTo("alice")
            row[Users.email].shouldBeEqualTo("alice@example.com")
            row[Users.age].shouldBeEqualTo(30)
        }
    }

    @Test
    fun `select with WHERE`() {
        withTables(Users) {
            Users.insertAndGetId {
                it[username] = "bob"
                it[email] = "bob@example.com"
                it[age] = 25
            }
            Users.insertAndGetId {
                it[username] = "carol"
                it[email] = "carol@example.com"
                it[age] = 40
            }

            val byUsername = Users.selectAll().where { Users.username eq "bob" }.singleOrNull()
            byUsername.shouldNotBeNull()
            byUsername[Users.email].shouldBeEqualTo("bob@example.com")

            val olderThan30 = Users.selectAll().where { Users.age greater 30 }.toList()
            olderThan30.size.shouldBeEqualTo(1)
            olderThan30[0][Users.username].shouldBeEqualTo("carol")
        }
    }

    @Test
    fun `update single row`() {
        withTables(Users) {
            Users.insertAndGetId {
                it[username] = "dave"
                it[email] = "dave@example.com"
                it[age] = 20
            }

            Users.update({ Users.username eq "dave" }) {
                it[email] = "dave-updated@example.com"
            }

            val row = Users.selectAll().where { Users.username eq "dave" }.singleOrNull()
            row.shouldNotBeNull()
            row[Users.email].shouldBeEqualTo("dave-updated@example.com")
        }
    }

    @Test
    fun `delete single row`() {
        withTables(Users) {
            val id = Users.insertAndGetId {
                it[username] = "eve"
                it[email] = "eve@example.com"
                it[age] = null
            }

            Users.deleteWhere { Users.id eq id }

            val row = Users.selectAll().where { Users.id eq id }.singleOrNull()
            row.shouldBeNull()
        }
    }

    @Test
    fun `count and limit offset`() {
        withTables(Users) {
            repeat(10) { i ->
                Users.insertAndGetId {
                    it[username] = "user$i"
                    it[email] = "user$i@example.com"
                    it[age] = 20 + i
                }
            }

            val total = Users.selectAll().count()
            total.shouldBeEqualTo(10L)

            val page = Users.selectAll().limit(3).offset(2).toList()
            page.size.shouldBeEqualTo(3)
        }
    }

    @Test
    fun `batch insert`() {
        withTables(Users) {
            val items = (1..5).map { i -> "batch$i" to "batch$i@example.com" }

            Users.batchInsert(items) { (name, mail) ->
                this[Users.username] = name
                this[Users.email] = mail
                this[Users.age] = null
            }

            val count = Users.selectAll().count()
            count.shouldBeEqualTo(5L)
            count.shouldBeGreaterThan(0L)
        }
    }
}
