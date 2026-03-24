dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-neo4j"))
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.testcontainers_neo4j)
}
