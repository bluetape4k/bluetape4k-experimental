package io.bluetape4k.spring.data.exposed.r2dbc

import io.bluetape4k.spring.data.exposed.r2dbc.config.EnableCoroutineExposedRepositories
import io.bluetape4k.spring.data.exposed.r2dbc.domain.Users
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration

@SpringBootTest(classes = [AbstractCoroutineExposedRepositoryTest.TestConfig::class])
abstract class AbstractCoroutineExposedRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableCoroutineExposedRepositories(
        basePackages = ["io.bluetape4k.spring.data.exposed.r2dbc.repository"]
    )
    class TestConfig

    @BeforeEach
    fun setUp() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users)
            Users.deleteAll()
        }
    }
}
