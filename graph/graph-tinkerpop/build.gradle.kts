dependencies {
    api(project(":graph-core"))
    api(Libs.tinkerpop_gremlin_core)
    api(Libs.tinkergraph_gremlin)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
}
