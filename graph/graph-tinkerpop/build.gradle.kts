dependencies {
    api(project(":graph-core"))
    testImplementation(project(":graph-servers"))
    
    api(Libs.tinkerpop_gremlin_core)
    api(Libs.tinkergraph_gremlin)

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    testImplementation(Libs.kotlinx_coroutines_test)

    testImplementation(Libs.bluetape4k_junit5)
}
