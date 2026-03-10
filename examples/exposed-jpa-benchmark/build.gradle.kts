plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id(Plugins.spring_boot)
    id(Plugins.gatling)
}

dependencies {
    // Exposed
    implementation(Libs.exposed_spring_boot4_starter)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_dao)
    implementation(Libs.exposed_java_time)

    // JPA
    implementation(Libs.springBootStarter("data-jpa"))

    // Web
    implementation(Libs.springBootStarter("web"))

    // DB - PostgreSQL (Testcontainers로 자동 시작)
    implementation(Libs.bluetape4k_testcontainers)
    implementation(Libs.testcontainers)
    implementation(Libs.testcontainers_postgresql)
    runtimeOnly(Libs.postgresql_driver)
    // H2 fallback (테스트용)
    runtimeOnly(Libs.h2_v2)

    // Test
    testImplementation(Libs.springBootStarter("test"))

    // Gatling
    gatling(Libs.gatling_charts_highcharts)
    gatling(Libs.gatling_http_java)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.bootJar { enabled = true }
tasks.jar { enabled = false }

// Gatling 런타임은 Java 21 기반이므로 Gatling 소스는 Java 21 타겟으로 컴파일
tasks.withType<JavaCompile>().configureEach {
    if (name.startsWith("compileGatling")) {
        options.release.set(21)
    }
}
