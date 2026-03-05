plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
}

dependencies {
    implementation(project(":exposed-r2dbc-spring-data"))
    implementation(Libs.springBootStarter("webflux"))
    implementation(Libs.exposed_spring_boot4_starter)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_dao)
    implementation(Libs.exposed_java_time)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactor)
    runtimeOnly(Libs.h2_v2)

    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.kotlinx_coroutines_test)
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
