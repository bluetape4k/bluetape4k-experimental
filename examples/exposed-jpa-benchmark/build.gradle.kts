plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.spring.boot4)
    alias(libs.plugins.gatling)
}

dependencies {
    // Exposed
    implementation(libs.exposed.spring.boot4.starter)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.java.time)

    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // DB - PostgreSQL (Testcontainers로 자동 시작)
    implementation(libs.bluetape4k.testcontainers)
    implementation(libs.testcontainers.lib)
    implementation(libs.testcontainers.postgresql)
    runtimeOnly(libs.postgresql.driver)
    // H2 fallback (테스트용)
    runtimeOnly(libs.h2.v2)

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Gatling
    gatling(libs.gatling.charts.highcharts)
    gatling(libs.gatling.http.java)
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
