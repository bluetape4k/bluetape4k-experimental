dependencies {
    api(Libs.lettuce_core)
    api(Libs.caffeine)

    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactive)

    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
}
