plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    api(libs.exposed.json)

    // CockroachDB는 PostgreSQL JDBC 드라이버 사용
    compileOnly(libs.postgresql.driver)

    testImplementation(libs.exposed.migration.jdbc)
    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.postgresql.driver)
    testImplementation(libs.testcontainers.cockroachdb)

    testImplementation(libs.hikaricp)
    testImplementation(libs.kotlinx.serialization.json)
}
