plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
}

dependencies {
    api(project(":hibernate-cache-lettuce"))
    api(project(":cache-lettuce-near"))
    api(Libs.bluetape4k_io)
    api(Libs.bluetape4k_redis)
    api(Libs.springBoot("autoconfigure"))

    compileOnly(Libs.springBootStarter("data-jpa"))
    compileOnly(Libs.springBoot("hibernate"))   // HibernatePropertiesCustomizer (Spring Boot 4)
    compileOnly(Libs.hibernate_core)
    compileOnly(Libs.micrometer_core)
    compileOnly(Libs.springBootStarter("actuator"))

    implementation(Libs.fory_kotlin)
    implementation(Libs.zstd_jni)

    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.springBootStarter("data-jpa"))
    testImplementation(Libs.springBootStarter("actuator"))
    testImplementation(Libs.micrometer_core)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
