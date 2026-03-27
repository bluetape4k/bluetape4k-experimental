dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.bluetape4k_logging)
    api(Libs.libphonenumber)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
}
