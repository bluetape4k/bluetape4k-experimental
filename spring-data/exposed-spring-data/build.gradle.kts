plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(Libs.kotlin_reflect)
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_spring7_transaction)
    api(Libs.springData("commons"))
    api(Libs.bluetape4k_exposed_core)
    api(Libs.bluetape4k_exposed_dao)

    compileOnly(Libs.springBoot("autoconfigure"))
    compileOnly(Libs.springBootStarter("data-jdbc"))

    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.exposed_spring_boot4_starter)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
