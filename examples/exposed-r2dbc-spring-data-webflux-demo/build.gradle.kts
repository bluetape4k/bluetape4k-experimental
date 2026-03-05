plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
}

dependencies {
    implementation(project(":exposed-r2dbc-spring-data"))

    implementation(Libs.exposed_r2dbc)
    implementation(Libs.exposed_java_time)
    implementation(Libs.bluetape4k_r2dbc)

    runtimeOnly(Libs.r2dbc_h2)

    implementation(Libs.springBootStarter("webflux"))
    testImplementation(Libs.springBootStarter("test"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactor)
    testImplementation(Libs.kotlinx_coroutines_test)
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
