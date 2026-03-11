dependencies {
    api(Libs.lettuce_core)
    api(Libs.bluetape4k_io)
    api(Libs.bluetape4k_redis)
    api(Libs.bluetape4k_resilience4j)

    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.bluetape4k_exposed_jdbc)

    implementation(Libs.fory_kotlin)
    implementation(Libs.lz4_java)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
