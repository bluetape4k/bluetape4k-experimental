// ============================================================
// clinic-appointment 프로젝트용 최소화된 dependencyManagement 블록
//
// 기준:
//  - Spring Boot BOM이 이미 버전을 관리하는 라이브러리는 dependency() 핀 제거
//  - scheduling 모듈(appointment-*) 5개에서 실제 사용하지 않는 BOM 제거
//  - kotlinx_atomicfu는 scheduling 모듈에서 직접 사용하지 않으므로 제거
// ============================================================

// subprojects 내의 dependencyManagement 블록
dependencyManagement {
    setApplyMavenExclusions(false)

    imports {
        // 필수 BOM — 항상 유지
        mavenBom(Libs.bluetape4k_bom)
        mavenBom(Libs.spring_boot4_dependencies)

        // 테스트 인프라 BOM
        mavenBom(Libs.testcontainers_bom)
        mavenBom(Libs.junit_bom)

        // Kotlin/Coroutines BOM
        mavenBom(Libs.kotlinx_coroutines_bom)
        mavenBom(Libs.kotlin_bom)

        // Timefold Solver BOM (appointment-solver 모듈에서 사용)
        mavenBom(Libs.timefold_solver_bom)

        // ── 삭제된 BOM ──────────────────────────────────────────────
        // feign_bom          → scheduling 모듈 미사용
        // micrometer_bom     → scheduling 모듈 미사용
        // micrometer_tracing_bom → scheduling 모듈 미사용
        // opentelemetry_bom  → scheduling 모듈 미사용
        // log4j_bom          → Spring Boot BOM이 커버
        // okhttp3_bom        → scheduling 모듈 미사용
        // netty_bom          → scheduling 모듈 미사용
        // jackson_bom        → Spring Boot BOM이 커버
        // jackson3_bom       → scheduling 모듈 미사용
    }

    dependencies {
        // JetBrains annotations (Spring Boot BOM 미포함)
        dependency(Libs.jetbrains_annotations)

        // Coroutines — BOM이 있어도 개별 버전 핀으로 일관성 보장
        dependency(Libs.kotlinx_coroutines_core)
        dependency(Libs.kotlinx_coroutines_core_jvm)
        dependency(Libs.kotlinx_coroutines_reactor)      // appointment-core: kotlinx_coroutines_reactor
        dependency(Libs.kotlinx_coroutines_slf4j)
        dependency(Libs.kotlinx_coroutines_debug)
        dependency(Libs.kotlinx_coroutines_test)
        dependency(Libs.kotlinx_coroutines_test_jvm)

        // JUnit 5 (BOM이 있지만 개별 artifact 핀)
        dependency(Libs.junit_jupiter)
        dependency(Libs.junit_jupiter_api)
        dependency(Libs.junit_jupiter_engine)
        dependency(Libs.junit_jupiter_migrationsupport)
        dependency(Libs.junit_jupiter_params)
        dependency(Libs.junit_platform_commons)
        dependency(Libs.junit_platform_engine)
        dependency(Libs.junit_platform_launcher)
        dependency(Libs.junit_platform_runner)

        // 테스트 유틸리티 (Spring Boot BOM 미포함)
        dependency(Libs.kluent)
        dependency(Libs.assertj_core)
        dependency(Libs.mockk)
        dependency(Libs.datafaker)
        dependency(Libs.random_beans)

        // Redis — appointment-notification에서 lettuce_core 직접 사용
        // (Spring Boot BOM은 lettuce-core를 관리하지만 version override 필요시 유지)
        dependency(Libs.lettuce_core)

        // ── 삭제된 dependency() 항목 ────────────────────────────────
        // kotlinx_coroutines_bom (BOM으로 import했으므로 dependency 핀 불필요)
        // kotlinx_coroutines_reactive → scheduling 모듈 미사용
        //
        // commons_beanutils, commons_collections4, commons_compress,
        // commons_codec, commons_csv, commons_lang3, commons_logging,
        // commons_math3, commons_pool2, commons_text, commons_exec, commons_io
        //   → scheduling 모듈 직접 미사용; Spring Boot BOM/transitives로 해결
        //
        // slf4j_api, jcl_over_slf4j, jul_to_slf4j, log4j_over_slf4j,
        // logback, logback_core
        //   → Spring Boot BOM이 커버
        //
        // jakarta_activation_api, jakarta_annotation_api, jakarta_el_api,
        // jakarta_inject_api, jakarta_interceptor_api, jakarta_jms_api,
        // jakarta_json_api, jakarta_json, jakarta_persistence_api,
        // jakarta_servlet_api, jakarta_transaction_api, jakarta_validation_api,
        // jakarta_ws_rs_api, jakarta_xml_bind
        //   → Spring Boot BOM이 커버
        //
        // jackson_annotations, jackson_core → Spring Boot BOM이 커버
        // jackson3_core                     → jackson3 미사용
        //
        // snappy_java, lz4_java, zstd_jni   → scheduling 모듈 미사용
        // findbugs, guava                    → scheduling 모듈 미사용
        // kryo5, fory_kotlin                 → scheduling 모듈 미사용
        // caffeine, caffeine_jcache          → scheduling 모듈 미사용
        // objenesis, ow2_asm, reflectasm     → scheduling 모듈 미사용
        // jsonpath, jsonassert               → scheduling 모듈 미사용
        // redisson                           → scheduling 모듈 미사용
        // junit_bom (dependency)             → BOM으로 import했으므로 불필요
    }
}

// ============================================================
// subprojects { dependencies {} } — 최소화 버전
//
// 제거 항목:
//  - kotlinx_atomicfu: scheduling 모듈 직접 미사용
//    (root build.gradle.kts의 atomicfu plugin 설정도 clinic-appointment에 불필요)
//  - awaitility_kotlin: scheduling 모듈 미사용 (필요 시 개별 모듈에서 추가)
//
// 유지 항목:
//  - kotlin_stdlib, kotlin_reflect, kotlin_test, kotlin_test_junit5: 필수
//  - kotlinx_coroutines_core: appointment-core에서 api()로 노출
//  - slf4j_api, bluetape4k_logging, logback: 로깅 인프라 필수
//  - jcl_over_slf4j, jul_to_slf4j, log4j_over_slf4j: SLF4J bridge (test)
//  - bluetape4k_junit5, junit_jupiter, junit_platform_engine: 테스트 필수
//  - kluent, mockk, datafaker, random_beans: 테스트 유틸리티
// ============================================================

subprojects {
    // ... (plugins apply, java toolchain, kotlin, tasks 설정은 root 그대로 유지)

    dependencies {
        val api by configurations
        val implementation by configurations
        val testImplementation by configurations
        val compileOnly by configurations
        val testCompileOnly by configurations
        val testRuntimeOnly by configurations

        compileOnly(platform(Libs.bluetape4k_bom))
        compileOnly(platform(Libs.spring_boot4_dependencies))
        compileOnly(platform(Libs.kotlinx_coroutines_bom))

        implementation(Libs.kotlin_stdlib)
        implementation(Libs.kotlin_reflect)
        testImplementation(Libs.kotlin_test)
        testImplementation(Libs.kotlin_test_junit5)

        implementation(Libs.kotlinx_coroutines_core)
        // kotlinx_atomicfu 제거 — scheduling 모듈 미사용

        implementation(Libs.slf4j_api)
        implementation(Libs.bluetape4k_logging)
        implementation(Libs.logback)
        testImplementation(Libs.jcl_over_slf4j)
        testImplementation(Libs.jul_to_slf4j)
        testImplementation(Libs.log4j_over_slf4j)

        // JUnit 5
        testImplementation(Libs.bluetape4k_junit5)
        testImplementation(Libs.junit_jupiter)
        testRuntimeOnly(Libs.junit_platform_engine)

        testImplementation(Libs.kluent)
        testImplementation(Libs.mockk)
        // awaitility_kotlin 제거 — scheduling 모듈 미사용

        testImplementation(Libs.datafaker)
        testImplementation(Libs.random_beans)
    }
}
