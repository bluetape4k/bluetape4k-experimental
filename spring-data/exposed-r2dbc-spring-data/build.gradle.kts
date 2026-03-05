plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(Libs.springData("commons"))

    api(project(":exposed-spring-data"))  // EntityInformation, ExposedMappingContext 재사용
    api(Libs.kotlin_reflect)
    api(Libs.exposed_core)
    api(Libs.exposed_r2dbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_spring7_transaction)

    testImplementation(Libs.exposed_migration_r2dbc)
    testImplementation(Libs.flyway_core)

    api(Libs.bluetape4k_exposed_core)
    api(Libs.bluetape4k_exposed_r2dbc)
    testImplementation(Libs.bluetape4k_exposed_r2dbc_tests)

    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_reactor)  // Spring Data 코루틴 지원 요구사항
    testImplementation(Libs.kotlinx_coroutines_test)

    compileOnly(Libs.springBoot("autoconfigure"))

    testImplementation(Libs.springBootStarter("test"))

    testImplementation(Libs.h2_v2)
    testImplementation(Libs.r2dbc_h2)
    testImplementation(Libs.hikaricp)
}
