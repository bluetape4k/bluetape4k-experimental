dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_gcloud)

    // BigQuery REST API 클라이언트 (에뮬레이터 직접 연결용)
    testImplementation(Libs.google_api_services_bigquery)
}
