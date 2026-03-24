dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-memgraph"))
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.testcontainers)
}
