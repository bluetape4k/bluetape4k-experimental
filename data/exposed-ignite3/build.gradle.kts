dependencies {
    api(bt4k.exposed.core)
    api(libs.jetbrains.exposed.dao)
    api(bt4k.exposed.jdbc)
    api(bt4k.exposed.java.time)
    api(bt4k.bluetape4k.exposed.jdbc)

    api(libs.ignite3.client)
    api(libs.ignite3.jdbc)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)

    testImplementation(bt4k.bluetape4k.exposed.jdbc.tests)

    testImplementation(bt4k.flyway.core)
    testImplementation(bt4k.hikaricp)
}
