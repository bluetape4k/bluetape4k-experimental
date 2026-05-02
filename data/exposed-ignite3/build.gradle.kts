dependencies {
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.jdbc)
    api(libs.exposed.java.time)
    api(libs.bluetape4k.exposed.jdbc)

    api(libs.ignite3.client)
    api(libs.ignite3.jdbc)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)

    testImplementation(libs.bluetape4k.exposed.jdbc.tests)

    testImplementation(libs.flyway.core)
    testImplementation(libs.hikaricp)
}
