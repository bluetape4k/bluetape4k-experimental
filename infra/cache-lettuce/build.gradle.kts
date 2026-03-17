dependencies {
    api(Libs.lettuce_core)
    api(Libs.caffeine)

    api(Libs.bluetape4k_io)
    api(Libs.bluetape4k_redis)
    testImplementation(Libs.bluetape4k_junit5)

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactive)
    testImplementation(Libs.kotlinx_coroutines_test)

    implementation(Libs.kryo5)
    implementation(Libs.fory_kotlin)

    implementation(Libs.lz4_java)
    implementation(Libs.zstd_jni)

    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)

}
