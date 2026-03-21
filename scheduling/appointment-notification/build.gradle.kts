plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":appointment-core"))
    api(project(":appointment-event"))

    implementation(Libs.exposed_jdbc)
    implementation(Libs.bluetape4k_exposed_jdbc)
    implementation(Libs.springBootStarter("web"))

    // HA: Leader election for scheduler throttling
    implementation(Libs.bluetape4k_leader)
    implementation(Libs.bluetape4k_lettuce)
    implementation(Libs.lettuce_core)

    // Resilience4j: CircuitBreaker, Retry, Bulkhead
    implementation(Libs.bluetape4k_resilience4j)
    implementation(Libs.resilience4j_circuitbreaker)
    implementation(Libs.resilience4j_retry)
    implementation(Libs.resilience4j_bulkhead)
    implementation(Libs.resilience4j_kotlin)

    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kluent)
    testImplementation(Libs.mockk)

    runtimeOnly(Libs.h2_v2)
}
