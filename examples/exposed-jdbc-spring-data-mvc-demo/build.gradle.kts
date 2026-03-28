plugins {
    kotlin("plugin.spring")
    id(Plugins.spring_boot)
}

dependencies {
    implementation(project(":exposed-jdbc-spring-data"))
    implementation(Libs.springBootStarter("web"))
    implementation(Libs.exposed_spring_boot4_starter)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_dao)
    implementation(Libs.exposed_migration_jdbc)
    implementation(Libs.exposed_java_time)
    runtimeOnly(Libs.h2_v2)

    testImplementation(Libs.springBootStarter("test"))
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
