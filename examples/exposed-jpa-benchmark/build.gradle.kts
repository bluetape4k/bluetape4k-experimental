plugins {
    alias(bt4k.plugins.kotlin.spring)
    alias(bt4k.plugins.kotlin.jpa)
    alias(bt4k.plugins.kotlin.allopen)
    alias(bt4k.plugins.spring.boot4)
    alias(bt4k.plugins.gatling)
}

dependencies {
    // Exposed
    implementation(bt4k.exposed.spring.boot4.starter)
    implementation(bt4k.exposed.jdbc)
    implementation(bt4k.exposed.dao)
    implementation(bt4k.exposed.java.time)

    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // DB - PostgreSQL (Testcontainers로 자동 시작)
    implementation(bt4k.bluetape4k.testcontainers)
    implementation(libs.testcontainers.lib)
    implementation(libs.testcontainers.postgresql)
    runtimeOnly(bt4k.postgresql)
    // H2 fallback (테스트용)
    runtimeOnly(bt4k.h2.v2)

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
