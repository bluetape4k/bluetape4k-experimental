plugins {
    kotlin("jvm")
}

dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.jts_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)
    testImplementation(Libs.mysql_connector_j)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_mysql)
}
