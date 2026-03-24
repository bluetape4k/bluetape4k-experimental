dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-tinkerpop"))
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
}
