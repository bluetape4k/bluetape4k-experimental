plugins {
    kotlin("plugin.serialization")
}

dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_json)

    // CockroachDB는 PostgreSQL JDBC 드라이버 사용
    compileOnly(Libs.postgresql_driver)

    testImplementation(Libs.exposed_migration_jdbc)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.testcontainers_cockroachdb)
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.kotlinx_serialization_json)
}
