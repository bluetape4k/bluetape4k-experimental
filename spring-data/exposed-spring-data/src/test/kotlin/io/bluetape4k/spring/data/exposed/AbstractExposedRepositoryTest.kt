package io.bluetape4k.spring.data.exposed

import io.bluetape4k.spring.data.exposed.domain.Users
import io.bluetape4k.spring.data.exposed.repository.config.EnableExposedRepositories
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration

@SpringBootTest(classes = [AbstractExposedRepositoryTest.TestConfig::class])
abstract class AbstractExposedRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableExposedRepositories(basePackages = ["io.bluetape4k.spring.data.exposed.repository"])
    class TestConfig

    @BeforeEach
    fun setUp() {
        transaction {
//            MigrationUtils.statementsRequiredForDatabaseMigration(Users).forEach {stmt ->
//                exec(stmt)
//            }
            SchemaUtils.createMissingTablesAndColumns(Users)
            Users.deleteAll()
        }
    }
}
