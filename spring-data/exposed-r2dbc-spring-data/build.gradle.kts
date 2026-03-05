plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":spring-data-exposed-spring-data"))  // EntityInformation, ExposedMappingContext 재사용
    api(Libs.kotlin_reflect)
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_spring7_transaction)
    api(Libs.springData("commons"))
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_reactor)  // Spring Data 코루틴 지원 요구사항
    api(Libs.bluetape4k_exposed_core)
    api(Libs.bluetape4k_exposed_dao)

    compileOnly(Libs.springBoot("autoconfigure"))

    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.exposed_spring_boot4_starter)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
