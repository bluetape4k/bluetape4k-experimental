package io.bluetape4k.exposed.cockroachdb.schema

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.domain.UserUUIDs
import io.bluetape4k.exposed.cockroachdb.domain.Users
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeEqualTo
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.junit.jupiter.api.Test

class PkTest : AbstractCockroachDBTest() {

    @Test
    fun `LongIdTable auto-generates positive unique ids`() {
        withTables(Users) {
            val id1 = Users.insertAndGetId {
                it[username] = "alice"
                it[email] = "alice@example.com"
            }
            val id2 = Users.insertAndGetId {
                it[username] = "bob"
                it[email] = "bob@example.com"
            }
            id1.value shouldBeGreaterThan 0L
            id2.value shouldBeGreaterThan 0L
            id1 shouldNotBeEqualTo id2
        }
    }

    @Test
    fun `UUIDTable auto-generates valid UUID`() {
        withTables(UserUUIDs) {
            val id = UserUUIDs.insertAndGetId {
                it[username] = "charlie"
            }
            id.value.toString().length shouldBeEqualTo 36  // UUID format
        }
    }

    @Test
    fun `multiple inserts produce unique ids`() {
        withTables(Users) {
            val ids = (1..5).map { i ->
                Users.insertAndGetId {
                    it[username] = "user$i"
                    it[email] = "user$i@example.com"
                }.value
            }
            ids.toSet().size shouldBeEqualTo 5  // 모두 고유
        }
    }
}
