dependencies {
    api(Libs.bluetape4k_core)
    api(Libs.bluetape4k_logging)
    api(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
}
