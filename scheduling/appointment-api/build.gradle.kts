plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
    id(Plugins.gatling)
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

    // Gatling
    gatling(Libs.gatling_charts_highcharts)
    gatling(Libs.gatling_http_java)
}

// Gatling 런타임은 Java 21 기반이므로 Gatling 소스는 Java 21 타겟으로 컴파일
tasks.withType<JavaCompile>().configureEach {
    if (name.startsWith("compileGatling")) {
        options.release.set(21)
    }
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
