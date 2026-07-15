plugins {
    alias(bt4k.plugins.kotlin.serialization)
}

dependencies {
    api(bt4k.exposed.core)
    api(bt4k.exposed.dao)
    api(bt4k.exposed.jdbc)
    api(bt4k.exposed.java.time)
    api(bt4k.exposed.json)

    // CockroachDB는 PostgreSQL JDBC 드라이버 사용
    compileOnly(bt4k.postgresql)

    testImplementation(bt4k.exposed.migration.jdbc)
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(bt4k.postgresql)
    testImplementation(libs.testcontainers.cockroachdb)

    testImplementation(bt4k.hikaricp)
    testImplementation(bt4k.kotlinx.serialization.json)
}
