package io.bluetape4k.examples.benchmark.config

import io.bluetape4k.examples.benchmark.domain.exposed.Authors
import io.bluetape4k.examples.benchmark.domain.exposed.Books
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
class DataInitializer(
    private val dataSource: DataSource,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        // ExposedAutoConfiguration이 제외되어 있으므로 수동으로 DataSource 연결.
        // Spring 관리 DataSource(HikariCP)를 그대로 전달하므로 커넥션 풀은 공유됨.
        Database.connect(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Authors, Books)
        }
    }
}
