dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_gcloud)

    // BigQuery REST API 클라이언트 (에뮬레이터 및 프로덕션 연결용)
    implementation(Libs.google_api_services_bigquery)
}
