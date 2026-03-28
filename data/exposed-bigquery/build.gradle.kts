repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.google_api_services_bigquery)

    // BigQueryContext.create() 가 H2 sqlGenDb 를 내부 생성하므로 런타임 classpath 에 필요하다.
    implementation(Libs.h2_v2)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_gcloud)
}
