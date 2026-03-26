dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.bluetape4k_exposed_jdbc)

    api(Libs.ignite3_client)
    api(Libs.ignite3_jdbc)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)

    testImplementation(Libs.flyway_core)
    testImplementation(Libs.hikaricp)
}
