package io.bluetape4k.examples.benchmark

import io.bluetape4k.testcontainers.database.PostgreSQLServer
import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// ExposedAutoConfiguration을 제외하여 JPA의 transactionManager 빈과 충돌을 방지.
// Exposed DataSource 연결은 DataInitializer에서 Database.connect(dataSource)로 직접 수행.
@SpringBootApplication(exclude = [ExposedAutoConfiguration::class])
class BenchmarkApplication

fun main(args: Array<String>) {
    // PostgreSQL Testcontainers 컨테이너를 Spring 컨텍스트 초기화 전에 시작
    val postgres = PostgreSQLServer.Launcher.postgres
    System.setProperty("spring.datasource.url", postgres.jdbcUrl)
    System.setProperty("spring.datasource.username", postgres.username.orEmpty())
    System.setProperty("spring.datasource.password", postgres.password.orEmpty())
    System.setProperty("spring.datasource.driver-class-name", PostgreSQLServer.DRIVER_CLASS_NAME)

    runApplication<BenchmarkApplication>(*args)
}
