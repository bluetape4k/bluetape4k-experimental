plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":appointment-core"))

    compileOnly(Libs.springBoot("autoconfigure"))
    compileOnly("org.springframework:spring-context")

    api(Libs.exposed_core)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_r2dbc)
    api(Libs.exposed_java_time)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_exposed_r2dbc_tests)
    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.r2dbc_h2)
    testImplementation(Libs.kotlinx_coroutines_test)
}
