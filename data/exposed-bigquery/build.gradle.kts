dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.testcontainers)

    // BigQuery JDBC 드라이버 (Maven Central 미배포 — 수동 설치 필요)
    // https://storage.googleapis.com/simba-bq-release/jdbc/ 에서 다운로드 후:
    //   mvn install:install-file -Dfile=BigQueryJDBC42.jar \
    //     -DgroupId=com.simba.googlebigquery -DartifactId=googlebigquery-jdbc42 \
    //     -Dversion=1.5.4 -Dpackaging=jar
    // 설치 후 아래 주석 해제:
    // testImplementation(Libs.bigquery_connector_jdbc)
}
