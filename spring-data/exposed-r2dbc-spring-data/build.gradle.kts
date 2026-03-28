plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(Libs.springData("commons"))

    api(project(":exposed-jdbc-spring-data"))  // EntityInformation, ExposedMappingContext 재사용
    api(Libs.kotlin_reflect)
    api(Libs.exposed_core)
    api(Libs.exposed_r2dbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_spring7_transaction)

    testImplementation(Libs.exposed_migration_r2dbc)
    testImplementation(Libs.flyway_core)
    testImplementation(Libs.bluetape4k_junit5)

    api(Libs.bluetape4k_exposed_core)
    api(Libs.bluetape4k_exposed_r2dbc)
    testImplementation(Libs.bluetape4k_exposed_r2dbc_tests)

    // Experimental 에서는 Java 25 를 사용한다.
    // testImplementation(Libs.bluetape4k_virtualthread_jdk21)
    testImplementation(Libs.bluetape4k_virtualthread_jdk25)

    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_reactor)  // Spring Data 코루틴 지원 요구사항
    testImplementation(Libs.kotlinx_coroutines_test)

    compileOnly(Libs.springBoot("autoconfigure"))

    testImplementation(Libs.springBootStarter("test"))

    testImplementation(Libs.h2_v2)
    testImplementation(Libs.r2dbc_h2)
    testImplementation(Libs.hikaricp)

    // Multi-DB 테스트용 R2DBC 드라이버
    testImplementation(Libs.r2dbc_mysql)
    testImplementation(Libs.r2dbc_mariadb)
    testImplementation(Libs.r2dbc_postgresql)

    // Multi-DB 테스트용 JDBC 드라이버 (Testcontainers 컨테이너 연결용)
    testImplementation(Libs.mysql_connector_j)
    testImplementation(Libs.mariadb_java_client)
    testImplementation(Libs.postgresql_driver)
}
