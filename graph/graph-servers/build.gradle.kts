configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.neo4j.bolt") {
            useVersion("1.1.0")
            because("neo4j-java-driver:5.28.4 requires bolt 1.1.0 API (BootstrapFactory)")
        }
    }
}

dependencies {
    // Apache AGE (PostgreSQL extension)
    api(Libs.testcontainers_postgresql)
    api(Libs.postgresql_driver)
    api(Libs.hikaricp)

    // Neo4j
    api(Libs.testcontainers_neo4j)
    api(Libs.neo4j_java_driver)
    runtimeOnly(Libs.neo4j_bolt_connection_netty)

    // Memgraph (generic container + neo4j driver)
    api(Libs.testcontainers)
    api(Libs.bluetape4k_testcontainers)

    api(Libs.bluetape4k_core)
}
