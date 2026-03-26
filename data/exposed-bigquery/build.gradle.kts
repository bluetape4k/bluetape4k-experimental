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

    // Simba BigQuery JDBC 드라이버 + 전이 의존성
    // 다운로드: https://storage.googleapis.com/simba-bq-release/jdbc/SimbaJDBCDriverforGoogleBigQuery42_1.6.5.1002.zip
    // 압축 해제 후 모든 JAR을 libs/ 디렉토리에 복사:
    //   cp /path/to/SimbaJDBC/*.jar data/exposed-bigquery/libs/
    testImplementation(fileTree("libs") { include("*.jar") })
}
