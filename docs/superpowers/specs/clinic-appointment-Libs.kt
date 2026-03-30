import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

// @formatter:off

object Plugins {

    object Versions {
        const val dokka = "2.1.0"      // https://mvnrepository.com/artifact/org.jetbrains.dokka/dokka-gradle-plugin
        const val detekt = "1.23.8"     // https://mvnrepository.com/artifact/io.gitlab.arturbosch.detekt/detekt-gradle-plugin
        const val dependency_management = "1.1.7"  // https://mvnrepository.com/artifact/io.spring.gradle/dependency-management-plugin

        const val jacoco = "0.8.11"
        const val jarTest = "1.0.1"
        const val testLogger = "4.0.0"
        const val shadow = "9.2.2"      // https://plugins.gradle.org/plugin/com.gradleup.shadow

        const val spring_boot3 = "3.5.11"  // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies
        const val spring_boot4 = "4.0.3"  // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies

        // 참고: https://docs.gatling.io/reference/integrations/build-tools/gradle-plugin/
        const val gatling = "3.15.0"  // https://plugins.gradle.org/plugin/io.gatling.gradle
    }

    const val detekt = "io.gitlab.arturbosch.detekt"
    const val dokka = "org.jetbrains.dokka"
    const val dependency_management = "io.spring.dependency-management"
    const val spring_boot = "org.springframework.boot"

    const val jarTest = "com.github.hauner.jarTest"

    // https://mvnrepository.com/artifact/com.adarshr/gradle-test-logger-plugin
    const val testLogger = "com.adarshr.test-logger"
    const val shadow = "com.gradleup.shadow" // https://plugins.gradle.org/plugin/com.gradleup.shadow

    // https://docs.gatling.io/reference/extensions/build-tools/gradle-plugin/
    const val gatling = "io.gatling.gradle"
}

object Versions {

    // Java 21, Kotlin 2.3 이상에서 사용하세요
    const val bluetape4k = "1.5.0-SNAPSHOT"    // https://mvnrepository.com/artifact/io.github.bluetape4k/bluetape4k-bom

    const val kotlin = "2.3.20"                 // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    const val kotlinx_coroutines = "1.10.2"     // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core

    const val spring_boot3 = Plugins.Versions.spring_boot3
    const val spring_boot4 = Plugins.Versions.spring_boot4

    const val springdoc_openapi = "3.0.0"    // https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui

    const val resilience4j = "2.3.0"   // https://mvnrepository.com/artifact/io.github.resilience4j/resilience4j-bom

    const val jjwt = "0.13.0"    // https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api

    const val lettuce = "6.8.2.RELEASE" // https://mvnrepository.com/artifact/io.lettuce/lettuce-core

    const val exposed = "1.1.1"         // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core

    const val slf4j = "2.0.17"       // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    const val logback = "1.5.32"     // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic

    const val timefold_solver = "1.31.0" // https://mvnrepository.com/artifact/ai.timefold.solver/timefold-solver-core

    const val junit_jupiter = "6.0.3"      // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    const val junit_platform = "6.0.3"     // https://mvnrepository.com/artifact/org.junit.platform/junit-platform-launcher
    const val kluent = "1.73"               // https://mvnrepository.com/artifact/org.amshove.kluent/kluent
    const val mockk = "1.14.9"              // https://mvnrepository.com/artifact/io.mockk/mockk
    const val testcontainers = "2.0.3"      // https://mvnrepository.com/artifact/org.testcontainers/testcontainers
    const val jna = "5.18.1"                // https://mvnrepository.com/artifact/net.java.dev.jna/jna

    // Gatling
    const val gatling = "3.15.0" // https://mvnrepository.com/artifact/io.gatling/gatling-core
}

object Libs {

    fun getOsClassifier(): String {
        val os = DefaultNativePlatform.getCurrentOperatingSystem()
        val osName = when {
            os.isMacOsX  -> "osx"
            os.isLinux   -> "linux"
            os.isWindows -> "windows"
            else         -> ""
        }

        return if (osName.isEmpty()) {
            osName
        } else {
            val architecture = DefaultNativePlatform.getCurrentArchitecture()
            println("architecture=$architecture")

            val archName = if (architecture.name.startsWith("aarch64")) "aarch_64" else "x86_64"
            "$osName-$archName".apply {
                println("classifier=$this")
            }
        }
    }

    const val jetbrains_annotations = "org.jetbrains:annotations:26.0.2" // https://mvnrepository.com/artifact/org.jetbrains/annotations


    // bluetape4k
    fun bluetape4k(module: String, version: String = Versions.bluetape4k) = "io.github.bluetape4k:bluetape4k-$module:$version"

    val bluetape4k_bom = bluetape4k("bom")

    val bluetape4k_core = bluetape4k("core")
    val bluetape4k_coroutines = bluetape4k("coroutines")
    val bluetape4k_junit5 = bluetape4k("junit5")
    val bluetape4k_logging = bluetape4k("logging")
    val bluetape4k_testcontainers = bluetape4k("testcontainers")

    // Virtual Thread
    val bluetape4k_virtualthread_api = bluetape4k("virtualthread-api")
    val bluetape4k_virtualthread_jdk21 = bluetape4k("virtualthread-jdk21")
    val bluetape4k_virtualthread_jdk25 = bluetape4k("virtualthread-jdk25")

    // IO
    val bluetape4k_avro = bluetape4k("avro")
    val bluetape4k_crypto = bluetape4k("crypto")
    val bluetape4k_csv = bluetape4k("csv")
    val bluetape4k_fastjson2 = bluetape4k("fastjson2")
    val bluetape4k_feign = bluetape4k("feign")
    val bluetape4k_grpc = bluetape4k("grpc")
    val bluetape4k_http = bluetape4k("http")
    val bluetape4k_io = bluetape4k("io")
    val bluetape4k_jackson2 = bluetape4k("jackson2")
    val bluetape4k_jackson3 = bluetape4k("jackson3")
    val bluetape4k_json = bluetape4k("json")
    val bluetape4k_netty = bluetape4k("netty")
    val bluetape4k_okio = bluetape4k("okio")
    val bluetape4k_protobuf = bluetape4k("protobuf")
    val bluetape4k_retrofit2 = bluetape4k("retrofit2")
    val bluetape4k_tink = bluetape4k("tink")
    val bluetape4k_vertx = bluetape4k("vertx")

    // Data
    val bluetape4k_cassandra = bluetape4k("cassandra")

    val bluetape4k_exposed = bluetape4k("exposed")
    val bluetape4k_exposed_bigquery = bluetape4k("exposed-bigquery")
    val bluetape4k_exposed_core = bluetape4k("exposed-core")
    val bluetape4k_exposed_dao = bluetape4k("exposed-dao")
    val bluetape4k_exposed_duckdb = bluetape4k("exposed-duckdb")
    val bluetape4k_exposed_fastjson2 = bluetape4k("exposed-fastjson2")
    val bluetape4k_exposed_jackson2 = bluetape4k("exposed-jackson2")
    val bluetape4k_exposed_jackson3 = bluetape4k("exposed-jackson3")
    val bluetape4k_exposed_jasypt = bluetape4k("exposed-jasypt")
    val bluetape4k_exposed_jdbc = bluetape4k("exposed-jdbc")
    val bluetape4k_exposed_jdbc_lettuce = bluetape4k("exposed-jdbc-lettuce")
    val bluetape4k_exposed_jdbc_redisson = bluetape4k("exposed-jdbc-redisson")
    val bluetape4k_exposed_jdbc_tests = bluetape4k("exposed-jdbc-tests")
    val bluetape4k_exposed_measured = bluetape4k("exposed-measured")
    val bluetape4k_exposed_mysql8 = bluetape4k("exposed-mysql8")
    val bluetape4k_exposed_postgresql = bluetape4k("exposed-postgresql")
    val bluetape4k_exposed_tink = bluetape4k("exposed-tink")

    val bluetape4k_hibernate = bluetape4k("hibernate")
    val bluetape4k_hibernate_cache_lettuce = bluetape4k("hibernate-cache-lettuce")
    val bluetape4k_hibernate_reactive = bluetape4k("hibernate-reactive")

    val bluetape4k_jdbc = bluetape4k("jdbc")
    val bluetape4k_mongodb = bluetape4k("mongodb")

    // Infrastructure
    val bluetape4k_bucket4j = bluetape4k("bucket4j")
    val bluetape4k_cache = bluetape4k("cache")
    val bluetape4k_cache_core = bluetape4k("cache-core")
    val bluetape4k_cache_hazelcast = bluetape4k("cache-hazelcast")
    val bluetape4k_cache_lettuce = bluetape4k("cache-lettuce")
    val bluetape4k_cache_redisson = bluetape4k("cache-redisson")
    val bluetape4k_kafka = bluetape4k("kafka")
    val bluetape4k_lettuce = bluetape4k("lettuce")
    val bluetape4k_micrometer = bluetape4k("micrometer")
    val bluetape4k_opentelemetry = bluetape4k("opentelemetry")
    val bluetape4k_redis = bluetape4k("redis")
    val bluetape4k_redisson = bluetape4k("redisson")
    val bluetape4k_resilience4j = bluetape4k("resilience4j")

    // Spring Boot 3
    val bluetape4k_spring_boot3_cassandra = bluetape4k("spring-boot3-cassandra")
    val bluetape4k_spring_boot3_core = bluetape4k("spring-boot3-core")
    val bluetape4k_spring_boot3_mongodb = bluetape4k("spring-boot3-mongodb")
    val bluetape4k_spring_boot3_redis = bluetape4k("spring-boot3-redis")

    // Spring Boot 4
    val bluetape4k_spring_boot4_cassandra = bluetape4k("spring-boot4-cassandra")
    val bluetape4k_spring_boot4_core = bluetape4k("spring-boot4-core")
    val bluetape4k_spring_boot4_mongodb = bluetape4k("spring-boot4-mongodb")
    val bluetape4k_spring_boot4_redis = bluetape4k("spring-boot4-redis")

    // AWS
    val bluetape4k_aws = bluetape4k("aws")
    val bluetape4k_aws_kotlin = bluetape4k("aws-kotlin")

    // UTILS
    val bluetape4k_geo = bluetape4k("geo")
    val bluetape4k_idgenerators = bluetape4k("idgenerators")
    val bluetape4k_images = bluetape4k("images")
    val bluetape4k_javatimes = bluetape4k("javatimes")
    val bluetape4k_jwt = bluetape4k("jwt")
    val bluetape4k_leader = bluetape4k("leader")
    val bluetape4k_math = bluetape4k("math")
    val bluetape4k_measured = bluetape4k("measured")
    val bluetape4k_money = bluetape4k("money")
    val bluetape4k_mutiny = bluetape4k("mutiny")

    // Timefold
    val bluetape4k_timefold_solver_persistence_exposed = bluetape4k("timefold-solver-persistence-exposed")

    // kotlin
    fun kotlin(module: String, version: String = Versions.kotlin) = "org.jetbrains.kotlin:kotlin-$module:$version"

    val kotlin_bom = kotlin("bom")
    val kotlin_stdlib = kotlin("stdlib")
    val kotlin_stdlib_common = kotlin("stdlib-common")
    val kotlin_reflect = kotlin("reflect")
    val kotlin_test = kotlin("test")
    val kotlin_test_common = kotlin("test-common")
    val kotlin_test_junit5 = kotlin("test-junit5")

    // Kotlin 1.3.40 부터는 kotlin-scripting-jsr223 만 참조하면 됩니다.
    val kotlin_scripting_jsr223 = kotlin("scripting-jsr223")
    val kotlin_compiler = kotlin("compiler")

    // Kotlin 1.4+ 부터는 kotlin-scripting-dependencies 를 참조해야 합니다.
    val kotlin_scripting_dependencies = kotlin("scripting-dependencies")

    val kotlin_compiler_embeddable = kotlin("compiler-embeddable")
    val kotlin_daemon_client = kotlin("daemon-client")
    val kotlin_scripting_common = kotlin("scripting-common")
    val kotlin_scripting_compiler_embeddable = kotlin("scripting-compiler-embeddable")
    val kotlin_scripting_jvm = kotlin("scripting-jvm")
    val kotlin_script_runtime = kotlin("script-runtime")
    val kotlin_script_util = kotlin("scripting-util")

    fun kotlinxCoroutines(module: String, version: String = Versions.kotlinx_coroutines) =
        "org.jetbrains.kotlinx:kotlinx-coroutines-$module:$version"

    val kotlinx_coroutines_bom = kotlinxCoroutines("bom")
    val kotlinx_coroutines_core = kotlinxCoroutines("core")
    val kotlinx_coroutines_core_common = kotlinxCoroutines("core-common")
    val kotlinx_coroutines_core_jvm = kotlinxCoroutines("core-jvm")
    val kotlinx_coroutines_debug = kotlinxCoroutines("debug")
    val kotlinx_coroutines_reactive = kotlinxCoroutines("reactive")
    val kotlinx_coroutines_reactor = kotlinxCoroutines("reactor")
    val kotlinx_coroutines_rx2 = kotlinxCoroutines("rx2")
    val kotlinx_coroutines_rx3 = kotlinxCoroutines("rx3")
    val kotlinx_coroutines_slf4j = kotlinxCoroutines("slf4j")
    val kotlinx_coroutines_test = kotlinxCoroutines("test")
    val kotlinx_coroutines_test_jvm = kotlinxCoroutines("test-jvm")

    // Coroutines Flow를 Reactor처럼 테스트 할 수 있도록 해줍니다.
    // 참고: https://github.com/cashapp/turbine/
    const val turbine = "app.cash.turbine:turbine:1.1.0"
    const val turbine_jvm = "app.cash.turbine:turbine-jvm:1.1.0"

    fun slf4j(module: String, version: String = Versions.slf4j) = "org.slf4j:$module:$version"
    val slf4j_api = slf4j("slf4j-api")
    val slf4j_simple = slf4j("slf4j-simple")
    val slf4j_log4j12 = slf4j("slf4j-log4j2")
    val jcl_over_slf4j = slf4j("jcl-over-slf4j")
    val jul_to_slf4j = slf4j("jul-to-slf4j")
    val log4j_over_slf4j = slf4j("log4j-over-slf4j")

    const val logback = "ch.qos.logback:logback-classic:${Versions.logback}"
    const val logback_core = "ch.qos.logback:logback-core:${Versions.logback}"

    const val findbugs = "com.google.code.findbugs:jsr305:3.0.2"
    const val guava = "com.google.guava:guava:33.4.8-jre"  // https://mvnrepository.com/artifact/com.google.guava/guava

    // Spring Boot
    const val spring_boot4_dependencies = "org.springframework.boot:spring-boot-dependencies:${Versions.spring_boot4}"

    fun spring(module: String) = "org.springframework:spring-$module"
    fun springBoot(module: String) = "org.springframework.boot:spring-boot-$module"
    fun springBootStarter(module: String) = "org.springframework.boot:spring-boot-starter-$module"
    fun springData(module: String) = "org.springframework.data:spring-data-$module"

    fun springSecurity(module: String) = "org.springframework.security:spring-security-$module"

    // Resilience4j
    fun resilience4j(module: String, version: String = Versions.resilience4j) =
        "io.github.resilience4j:resilience4j-$module:$version"

    // resilience4j-bom 은 1.7.1 로 update 되지 않았다 (배포 실수인 듯)
    val resilience4j_bom = resilience4j("bom")
    val resilience4j_all = resilience4j("all")
    val resilience4j_annotations = resilience4j("annotations")
    val resilience4j_bulkhead = resilience4j("bulkhead")
    val resilience4j_cache = resilience4j("cache")
    val resilience4j_circuitbreaker = resilience4j("circuitbreaker")
    val resilience4j_circularbuffer = resilience4j("circularbuffer")
    val resilience4j_consumer = resilience4j("consumer")
    val resilience4j_core = resilience4j("core")
    val resilience4j_feign = resilience4j("feign")
    val resilience4j_framework_common = resilience4j("framework-common")
    val resilience4j_kotlin = resilience4j("kotlin")
    val resilience4j_metrics = resilience4j("metrics")
    val resilience4j_micrometer = resilience4j("micrometer")
    val resilience4j_ratelimiter = resilience4j("ratelimiter")
    val resilience4j_ratpack = resilience4j("ratpack")
    val resilience4j_reactor = resilience4j("reactor")
    val resilience4j_retrofit = resilience4j("retrofit")
    val resilience4j_retry = resilience4j("retry")
    val resilience4j_rxjava2 = resilience4j("rxjava2")
    val resilience4j_rxjava3 = resilience4j("rxjava3")
    val resilience4j_spring = resilience4j("spring")
    val resilience4j_spring_boot2 = resilience4j("spring-boot2")
    val resilience4j_spring_boot3 = resilience4j("spring-boot3")
    val resilience4j_spring_boot4 = resilience4j("spring-boot4")
    val resilience4j_spring_cloud2 = resilience4j("spring-cloud2")
    val resilience4j_timelimiter = resilience4j("timelimiter")
    val resilience4j_vertx = resilience4j("vertx")

    // JWT
    fun jjwt(module: String) = "io.jsonwebtoken:jjwt-$module:${Versions.jjwt}"
    val jjwt_api = jjwt("api")
    val jjwt_impl = jjwt("impl")
    val jjwt_jackson = jjwt("jackson")
    val jjwt_extensions = jjwt("extensions")

    // Redis
    const val lettuce_core = "io.lettuce:lettuce-core:${Versions.lettuce}"

    // Exposed
    fun exposed(module: String) = "org.jetbrains.exposed:exposed-$module:${Versions.exposed}"

    val exposed_bom = exposed("bom")
    val exposed_core = exposed("core")
    val exposed_crypt = exposed("crypt")
    val exposed_dao = exposed("dao")
    val exposed_java_time = exposed("java-time")
    val exposed_jdbc = exposed("jdbc")
    val exposed_json = exposed("json")
    val exposed_kotlin_datetime = exposed("kotlin-datetime")
    val exposed_migration_core = exposed("migration-core")
    val exposed_migration_jdbc = exposed("migration-jdbc")
    val exposed_money = exposed("money")
    val exposed_spring_boot_starter = exposed("spring-boot-starter")
    val exposed_spring_boot4_starter = exposed("spring-boot4-starter")
    const val exposed_spring_transaction = "org.jetbrains.exposed:spring-transaction:${Versions.exposed}"
    const val exposed_spring7_transaction = "org.jetbrains.exposed:spring7-transaction:${Versions.exposed}"

    // Flyway
    const val flyway_core = "org.flywaydb:flyway-core:11.15.0"  // https://mvnrepository.com/artifact/org.flywaydb/flyway-core
    const val flyway_database_postgresql = "org.flywaydb:flyway-database-postgresql:11.15.0"
    const val flyway_mysql = "org.flywaydb:flyway-mysql:11.15.0"

    // Database drivers
    const val postgresql_driver = "org.postgresql:postgresql:42.7.8"  // https://mvnrepository.com/artifact/org.postgresql/postgresql
    const val h2_v2 = "com.h2database:h2:2.4.240"    // https://mvnrepository.com/artifact/com.h2database/h2

    // Timefold Solver
    fun timefoldSolver(module: String, version: String = Versions.timefold_solver) = "ai.timefold.solver:timefold-solver-$module:$version"

    val timefold_solver_bom = timefoldSolver("bom")
    val timefold_solver_benchmark = timefoldSolver("benchmark")
    val timefold_solver_core = timefoldSolver("core")
    val timefold_solver_jackson = timefoldSolver("jackson")
    val timefold_solver_jaxb = timefoldSolver("jaxb")
    val timefold_solver_jsonb = timefoldSolver("jsonb")
    val timefold_solver_migration = timefoldSolver("migration")
    val timefold_solver_persistence_common = timefoldSolver("persistence-common")
    val timefold_solver_persistence_jpa = timefoldSolver("persistence-jpa")
    val timefold_solver_spring_boot_starter = timefoldSolver("spring-boot-starter")
    val timefold_solver_test = timefoldSolver("test")
    val timefold_solver_webui = timefoldSolver("webui")

    // Springdoc OpenAPI
    const val springdoc_openapi_starter_webmvc_api =
        "org.springdoc:springdoc-openapi-starter-webmvc-api:${Versions.springdoc_openapi}"
    const val springdoc_openapi_starter_webmvc_ui =
        "org.springdoc:springdoc-openapi-starter-webmvc-ui:${Versions.springdoc_openapi}"

    // junit 5.4+ 부터는 junit-jupiter 만 있으면 됩니다.
    const val junit_bom = "org.junit:junit-bom:${Versions.junit_jupiter}"

    fun junitJupiter(module: String) =
        "org.junit.jupiter:junit-jupiter-$module:${Versions.junit_jupiter}"

    const val junit_jupiter = "org.junit.jupiter:junit-jupiter:${Versions.junit_jupiter}"
    val junit_jupiter_api = junitJupiter("api")
    val junit_jupiter_engine = junitJupiter("engine")
    val junit_jupiter_migrationsupport = junitJupiter("migrationsupport")
    val junit_jupiter_params = junitJupiter("params")

    fun junitPlatform(module: String) = "org.junit.platform:junit-platform-$module:${Versions.junit_platform}"

    val junit_platform_commons = junitPlatform("commons")
    val junit_platform_engine = junitPlatform("engine")
    val junit_platform_launcher = junitPlatform("launcher")
    val junit_platform_runner = junitPlatform("runner")
    val junit_platform_suite_api = junitPlatform("suite-api")
    val junit_platform_suite_engine = junitPlatform("suite-engine")

    const val kluent = "org.amshove.kluent:kluent:${Versions.kluent}"

    const val mockk = "io.mockk:mockk:${Versions.mockk}"

    // -------------------------------------------------------------------------------------------
    // Testcontainers
    //
    private fun testcontainersCore(module: String) = "org.testcontainers:$module:${Versions.testcontainers}"
    private fun testcontainersModule(module: String) = "org.testcontainers:testcontainers-$module:${Versions.testcontainers}"

    val testcontainers_bom = testcontainersCore("testcontainers-bom")
    val testcontainers = testcontainersCore("testcontainers")
    val testcontainers_junit_jupiter = testcontainersModule("junit-jupiter")
    val testcontainers_cassandra = testcontainersModule("cassandra")
    val testcontainers_chromadb = testcontainersModule("chromadb")
    val testcontainers_clickhouse = testcontainersModule("clickhouse")
    val testcontainers_cockroachdb = testcontainersModule("cockroachdb")
    val testcontainers_couchbase = testcontainersModule("couchbase")
    val testcontainers_elasticsearch = testcontainersModule("elasticsearch")
    val testcontainers_influxdb = testcontainersModule("influxdb")
    val testcontainers_dynalite = testcontainersModule("dynalite")
    val testcontainers_mariadb = testcontainersModule("mariadb")
    val testcontainers_mongodb = testcontainersModule("mongodb")
    val testcontainers_mysql = testcontainersModule("mysql")
    val testcontaiiners_nginx = testcontainersModule("nginx")
    val testcontainers_ollama = testcontainersModule("ollama")
    val testcontainers_oracle_xe = testcontainersModule("oracle-xe")
    val testcontainers_postgresql = testcontainersModule("postgresql")
    val testcontainers_neo4j = testcontainersModule("neo4j")
    val testcontainers_kafka = testcontainersModule("kafka")
    val testcontainers_pulsar = testcontainersModule("pulsar")
    val testcontainers_redpanda = testcontainersModule("redpanda")
    val testcontainers_rabbitmq = testcontainersModule("rabbitmq")
    val testcontainers_selenuim = testcontainersModule("selenuim")
    val testcontainers_solace = testcontainersModule("solace")
    val testcontainers_vault = testcontainersModule("vault")

    // the Atlassian's LocalStack, 'a fully functional local AWS cloud stack'.
    val testcontainers_localstack = testcontainersModule("localstack")
    val testcontainers_mockserver = testcontainersModule("mockserver")

    val testcontainers_nginx = testcontainersModule("nginx")

    val testcontainers_gcloud = testcontainersModule("gcloud")

    // kubernetes
    val testcontainers_k3s = testcontainersModule("k3s")

    // Minio
    val testcontainers_minio = testcontainersModule("minio")

    // Weaviate
    val testcontainers_weaviate = testcontainersModule("weaviate")

    // Apple Silicon에서 testcontainers 를 사용하기 위해 참조해야 합니다.
    const val jna = "net.java.dev.jna:jna:${Versions.jna}"
    const val jna_platform = "net.java.dev.jna:jna-platform:${Versions.jna}"

    // Gatling (https://docs.gatling.io/)
    fun gatling(module: String) = "io.gatling:gatling-$module:${Versions.gatling}"
    val gatling_app = gatling("app")
    val gatling_core = gatling("core")
    val gatling_core_java = gatling("core-java")
    val gatling_http = gatling("http")
    val gatling_http_java = gatling("http-java")
    val gatling_jdbc = gatling("jdbc")
    val gatling_recorder = gatling("recorder")
    val gatling_test_framework = gatling("test-framework")
    const val gatling_charts_highcharts = "io.gatling.highcharts:gatling-charts-highcharts:${Versions.gatling}"

    // Detekt Plugins
    const val detekt_formatting = "io.gitlab.arturbosch.detekt:detekt-formatting:${Plugins.Versions.detekt}"

}

// @formatter:on
