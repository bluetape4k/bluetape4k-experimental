configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.neo4j.bolt") {
            useVersion("1.1.0")
            because("neo4j-java-driver:5.28.4 requires bolt 1.1.0 API (BootstrapFactory)")
        }
    }
}

dependencies {
    api(project(":graph-core"))
    api(Libs.neo4j_java_driver)
    runtimeOnly(Libs.neo4j_bolt_connection_netty)
    api(Libs.kotlinx_coroutines_reactive)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.kotlinx_coroutines_test)
}
