plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.jetbrains.exposed.core)
    api(libs.jetbrains.exposed.dao)
    api(libs.jetbrains.exposed.jdbc)
    api(libs.jetbrains.exposed.java.time)
    api(libs.jetbrains.exposed.json)

    // CockroachDB는 PostgreSQL JDBC 드라이버 사용
    compileOnly(libs.postgresql.driver)

    testImplementation(libs.jetbrains.exposed.migration.jdbc)
    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.postgresql.driver)
    testImplementation(libs.testcontainers.cockroachdb)

    testImplementation(libs.hikaricp)
    testImplementation(libs.kotlinx.serialization.json)
}
