package io.bluetape4k.exposed.cockroachdb.json

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.logging.KLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.json.contains
import org.jetbrains.exposed.v1.json.extract
import org.jetbrains.exposed.v1.json.jsonb
import org.junit.jupiter.api.Test

class JsonbTest : AbstractCockroachDBTest() {

    companion object : KLogging()

    @Serializable
    data class UserMetadata(val role: String, val score: Int, val active: Boolean)

    object JsonUsers : LongIdTable("json_users") {
        val name = varchar("name", 100)
        val metadata = jsonb<UserMetadata>("metadata", Json.Default)
    }

    @Test
    fun `insert and select JSONB column`() {
        withTables(JsonUsers) {
            val metadata = UserMetadata(role = "ADMIN", score = 42, active = true)

            val newId = JsonUsers.insertAndGetId {
                it[name] = "Alice"
                it[JsonUsers.metadata] = metadata
            }

            val row = JsonUsers.selectAll().where { JsonUsers.id eq newId }.singleOrNull()
            row.shouldNotBeNull()
            row[JsonUsers.name] shouldBeEqualTo "Alice"
            row[JsonUsers.metadata] shouldBeEqualTo metadata
        }
    }

    @Test
    fun `JSON field extract from JSONB column`() {
        withTables(JsonUsers) {
            val metadata = UserMetadata(role = "USER", score = 99, active = false)

            JsonUsers.insert {
                it[name] = "Bob"
                it[JsonUsers.metadata] = metadata
            }

            // PostgreSQL/CockroachDB JSON path: "role" field extraction
            val roleExtract = JsonUsers.metadata.extract<String>("role")
            val scoreExtract = JsonUsers.metadata.extract<Int>("score")

            // extract 표현식은 SELECT 절에 명시해야 결과 집합에 포함됨
            val row = JsonUsers.select(JsonUsers.id, JsonUsers.name, JsonUsers.metadata, roleExtract, scoreExtract)
                .singleOrNull()
            row.shouldNotBeNull()
            row[roleExtract] shouldBeEqualTo "USER"
            row[scoreExtract] shouldBeEqualTo 99
        }
    }

    @Test
    fun `JSONB contains check`() {
        withTables(JsonUsers) {
            val metadata = UserMetadata(role = "ADMIN", score = 10, active = true)

            JsonUsers.insert {
                it[name] = "Charlie"
                it[JsonUsers.metadata] = metadata
            }

            // @> 연산자: {"role":"ADMIN"} 포함 여부 확인
            val candidateJson = """{"role":"ADMIN"}"""
            val rows = JsonUsers.selectAll()
                .where { JsonUsers.metadata.contains(candidateJson) }
                .toList()

            rows.size shouldBeEqualTo 1
            rows[0][JsonUsers.name] shouldBeEqualTo "Charlie"
        }
    }
}
