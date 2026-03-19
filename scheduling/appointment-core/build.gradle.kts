plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_r2dbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_spring7_transaction)

    api(Libs.bluetape4k_exposed_core)
    api(Libs.bluetape4k_exposed_r2dbc)
    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_reactor)

    implementation(Libs.exposed_jdbc)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_exposed_r2dbc_tests)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.r2dbc_h2)
    testImplementation(Libs.kotlinx_coroutines_test)
}
