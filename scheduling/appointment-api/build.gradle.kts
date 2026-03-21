plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
}

dependencies {
    api(project(":appointment-core"))
    api(project(":appointment-event"))
    api(project(":appointment-solver"))

    implementation(Libs.springBootStarter("web"))
    implementation(Libs.springBootStarter("validation"))
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_spring_boot4_starter)
    implementation(Libs.bluetape4k_exposed_jdbc)

    runtimeOnly(Libs.h2_v2)

    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.springBoot("webmvc-test"))
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kluent)
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
