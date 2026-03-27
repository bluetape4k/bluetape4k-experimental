dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
}
