dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    testImplementation(project(":graph-servers"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    testImplementation(Libs.kotlinx_coroutines_test)

    testImplementation(Libs.bluetape4k_junit5)

    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.hikaricp)

}
