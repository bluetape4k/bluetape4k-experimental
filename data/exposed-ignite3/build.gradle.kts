dependencies {
    api(libs.jetbrains.exposed.core)
    api(libs.jetbrains.exposed.dao)
    api(libs.jetbrains.exposed.jdbc)
    api(libs.jetbrains.exposed.java.time)
    api(libs.exposed.jdbc)

    api(libs.ignite3.client)
    api(libs.ignite3.jdbc)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)

    testImplementation(libs.exposed.jdbc.tests)

    testImplementation(libs.flyway.core)
    testImplementation(libs.hikaricp)
}
