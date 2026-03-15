plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(Libs.springData("commons"))

    api(Libs.kotlin_reflect)
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_spring7_transaction)

    testImplementation(Libs.exposed_migration_jdbc)
    testImplementation(Libs.flyway_core)
    testImplementation(Libs.bluetape4k_junit5)

    api(Libs.bluetape4k_exposed_jdbc)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)

    compileOnly(Libs.springBoot("autoconfigure"))
    compileOnly(Libs.springBootStarter("data-jdbc"))
    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
