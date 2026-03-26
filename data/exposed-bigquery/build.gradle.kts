dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
