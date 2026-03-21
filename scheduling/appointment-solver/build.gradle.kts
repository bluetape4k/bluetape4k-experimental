dependencies {
    api(project(":appointment-core"))

    // Timefold Solver
    api(Libs.timefold_solver_core)
    implementation(Libs.timefold_solver_benchmark)

    implementation(Libs.bluetape4k_exposed_jdbc)
    implementation(Libs.exposed_jdbc)

    testImplementation(Libs.timefold_solver_test)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kluent)
    testImplementation(Libs.h2_v2)
}
