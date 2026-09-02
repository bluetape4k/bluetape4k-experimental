import dev.detekt.gradle.Detekt
import dev.detekt.gradle.report.ReportMergeTask
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    base
    alias(bt4k.plugins.kotlin.jvm)

    // see: https://kotlinlang.org/docs/reference/compiler-plugins.html
    alias(bt4k.plugins.kotlin.spring) apply false
    alias(bt4k.plugins.kotlin.allopen) apply false
    alias(bt4k.plugins.kotlin.noarg) apply false
    alias(bt4k.plugins.kotlin.jpa) apply false
    alias(bt4k.plugins.kotlin.serialization) apply false
    alias(bt4k.plugins.kotlinx.atomicfu)
    alias(bt4k.plugins.kover)

    alias(bt4k.plugins.detekt.dev)

    alias(bt4k.plugins.dependency.management)
    alias(bt4k.plugins.spring.boot4) apply false

    alias(bt4k.plugins.dokka)
    alias(bt4k.plugins.test.logger)
    alias(bt4k.plugins.shadow) apply false
    alias(bt4k.plugins.gatling) apply false
}

val rootLibs = libs
val rootBt4k = bt4k
val bt4kCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("bt4k")
fun bt4kLibrary(alias: String) = bt4kCatalog.findLibrary(alias).get()
fun bt4kVersion(alias: String): String {
    val version = bt4kCatalog.findVersion(alias).get()
    return version.requiredVersion
        .ifBlank { version.preferredVersion }
        .ifBlank { version.strictVersion }
}


allprojects {
    repositories {
        mavenCentral()
        google()

        // bluetape4k snapshot 버전 사용 시만 사용하세요.
        maven {
            name = "central-snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
    // bluetape4k snapshot 버전 사용 시만 사용하세요.
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(1, TimeUnit.DAYS)
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
    apply {
        plugin<JavaLibraryPlugin>()

        plugin("org.jetbrains.kotlin.jvm")

        // Atomicfu
        plugin("org.jetbrains.kotlinx.atomicfu")
        plugin("org.jetbrains.kotlinx.kover")

        plugin("io.spring.dependency-management")

        plugin("org.jetbrains.dokka")
        plugin("com.adarshr.test-logger")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_4)
            apiVersion.set(KotlinVersion.KOTLIN_2_4)
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-jvm-default=enable",
                // "-Xinline-classes",   // Kotlin 2.+ 에서는 불필요
                "-Xstring-concat=indy",
            )
            val experimentalAnnotations = listOf(
                "kotlin.RequiresOptIn",
                "kotlin.ExperimentalStdlibApi",
                "kotlin.contracts.ExperimentalContracts",
                "kotlin.experimental.ExperimentalTypeInference",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.InternalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview",
                "kotlinx.coroutines.DelicateCoroutinesApi",
            )
            freeCompilerArgs.addAll(experimentalAnnotations.map { "-opt-in=$it" })
        }
    }

    atomicfu {
        transformJvm = true
        jvmVariant = "VH"
    }

    tasks {
        compileJava {
            options.isIncremental = true
        }

        compileKotlin {
            compilerOptions {
                incremental = true
            }
        }

        abstract class TestMutexService: BuildService<BuildServiceParameters.None>

        val testMutex = gradle.sharedServices.registerIfAbsent(
            "test-mutex",
            TestMutexService::class
        ) {
            maxParallelUsages.set(1)
        }

        test {
            usesService(testMutex)
            useJUnitPlatform()
            testLogging {
                showExceptions = true
                showCauses = true
                showStackTraces = true
                events("failed")
            }
        }

        testlogger {
            theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
            showFullStackTraces = true
        }

        val reportMerge by registering(ReportMergeTask::class) {
            val file = rootProject.layout.buildDirectory.asFile.get().resolve("reports/detekt/merge.xml")
            output.set(file)
        }
        withType<Detekt>().configureEach detekt@{
            reports.checkstyle.required.set(true)
            finalizedBy(reportMerge)
            reportMerge.configure {
                input.from(this@detekt.reports.checkstyle.outputLocation)
            }
        }

        dokka {
            configureEach {
                dokkaSourceSets {
                    configureEach {
                        includes.from("README.md")
                    }
                }
                dokkaPublications.html {
                    outputDirectory.set(project.file("docs/api"))
                }
            }
        }

        clean {
            doLast {
                delete("./.project")
                delete("./out")
                delete("./bin")
            }
        }
    }

    dependencyManagement {
        setApplyMavenExclusions(false)

        imports {
            mavenBom(bt4kLibrary("bluetape4k-bom").get().toString())
            mavenBom(bt4kLibrary("bluetape4k-dependencies").get().toString())
            mavenBom("org.springframework.boot:spring-boot-dependencies:${bt4kVersion("spring-boot4")}")
            mavenBom(bt4kLibrary("exposed-bom").get().toString())

            mavenBom(rootBt4k.feign.bom.get().toString())
            mavenBom(rootBt4k.micrometer.bom.get().toString())
            mavenBom(rootBt4k.micrometer.tracing.bom.get().toString())
            mavenBom(bt4kLibrary("opentelemetry-bom").get().toString())
            mavenBom(bt4kLibrary("log4j-bom").get().toString())
            mavenBom("org.testcontainers:testcontainers-bom:${bt4kVersion("testcontainers")}")
            mavenBom(rootBt4k.junit.bom.get().toString())
            mavenBom(rootBt4k.okhttp3.bom.get().toString())
            mavenBom(bt4kLibrary("netty-bom").get().toString())
            mavenBom(rootBt4k.jackson2.bom.get().toString())
            mavenBom("tools.jackson:jackson-bom:${bt4kVersion("jackson3")}")

            mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")
            mavenBom("org.jetbrains.kotlin:kotlin-bom:${bt4kVersion("kotlin")}")
        }
        dependencies {
            // <central-catalog-local-aliases>
            dependency("io.gatling.highcharts:gatling-charts-highcharts:${bt4kVersion("gatling")}")
            dependency("io.gatling:gatling-http-java:${bt4kVersion("gatling")}")
            dependency("io.github.benas:random-beans:${bt4kVersion("random-beans")}")
            dependency("io.lettuce:lettuce-core:${bt4kVersion("lettuce")}")
            dependency("org.apache.ignite:ignite-client:${bt4kVersion("ignite3")}")
            dependency("org.apache.ignite:ignite-jdbc:${bt4kVersion("ignite3")}")
            dependency("org.awaitility:awaitility-kotlin:${bt4kVersion("awaitility")}")
            dependency("org.jetbrains.kotlin:kotlin-bom:${bt4kVersion("kotlin")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.slf4j:jcl-over-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:jul-to-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:log4j-over-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.springframework.boot:spring-boot-dependencies:${bt4kVersion("spring-boot4")}")
            dependency("org.testcontainers:testcontainers:${bt4kVersion("testcontainers")}")
            dependency("org.testcontainers:testcontainers-bom:${bt4kVersion("testcontainers")}")
            dependency("org.testcontainers:testcontainers-cockroachdb:${bt4kVersion("testcontainers")}")
            dependency("org.testcontainers:testcontainers-postgresql:${bt4kVersion("testcontainers")}")
            dependency("tools.jackson.core:jackson-core:${bt4kVersion("jackson3")}")
            dependency("tools.jackson:jackson-bom:${bt4kVersion("jackson3")}")
            // </central-catalog-local-aliases>
            dependency("org.postgresql:postgresql:${bt4kVersion("postgresql")}")
            dependency("io.r2dbc:r2dbc-h2:${bt4kVersion("r2dbc-h2")}")
            dependency("org.slf4j:slf4j-api:${bt4kVersion("slf4j")}")
            // Versions pinned explicitly (not managed by any imported BOM)
            dependency(rootBt4k.jetbrains.annotations.get().toString())

            // Apache Commons
            dependency(rootBt4k.commons.beanutils.get().toString())
            dependency(rootBt4k.commons.collections4.get().toString())
            dependency(bt4kLibrary("commons-compress").get().toString())
            dependency("commons-codec:commons-codec:${bt4kVersion("commons-codec")}")
            dependency("org.apache.commons:commons-csv:${bt4kVersion("commons-csv")}")
            dependency(bt4kLibrary("commons-lang3").get().toString())
            dependency("commons-logging:commons-logging:${bt4kVersion("commons-logging")}")
            dependency(rootBt4k.commons.math3.get().toString())
            dependency("org.apache.commons:commons-pool2:${bt4kVersion("commons-pool2")}")
            dependency(rootBt4k.commons.text.get().toString())
            dependency("org.apache.commons:commons-exec:${bt4kVersion("commons-exec")}")
            dependency("commons-io:commons-io:${bt4kVersion("commons-io")}")

            // Logging
            dependency(rootBt4k.logback.asProvider().get().toString())
            dependency(rootBt4k.logback.core.get().toString())

            // jakarta
            dependency(bt4kLibrary("jakarta-activation-api").get().toString())
            dependency(rootBt4k.jakarta.annotation.api.get().toString())
            dependency(rootBt4k.jakarta.el.api.get().toString())
            dependency(rootBt4k.jakarta.inject.api.get().toString())
            dependency(rootBt4k.jakarta.interceptor.api.get().toString())
            dependency(rootBt4k.jakarta.jms.api.get().toString())
            dependency(rootBt4k.jakarta.json.api.get().toString())
            dependency(rootBt4k.jakarta.json.glassfish.get().toString())
            dependency(rootBt4k.jakarta.persistence.v32.get().toString())
            dependency(rootBt4k.jakarta.servlet.api.get().toString())
            dependency(rootBt4k.jakarta.transaction.api.get().toString())
            dependency(rootBt4k.jakarta.validation.api.get().toString())
            dependency(rootBt4k.jakarta.ws.rs.api.get().toString())
            dependency("jakarta.xml.bind:jakarta.xml.bind-api:${bt4kVersion("jakarta-xml-bind")}")

            // Compressor
            dependency(rootBt4k.snappy.java.get().toString())
            dependency(rootBt4k.at.yawk.lz4.java.get().toString())
            dependency("com.github.luben:zstd-jni:${bt4kVersion("zstd-jni")}")

            dependency(rootBt4k.findbugs.get().toString())
            dependency("com.google.guava:guava:${bt4kVersion("guava")}")

            dependency(rootBt4k.kryo5.get().toString())
            dependency("org.apache.fory:fory-kotlin:${bt4kVersion("fory-kotlin")}")

            dependency(rootBt4k.caffeine.lib.get().toString())
            dependency(rootBt4k.caffeine.jcache.get().toString())

            dependency(rootBt4k.objenesis.get().toString())
            dependency("org.ow2.asm:asm:${bt4kVersion("ow2-asm")}")

            dependency(rootBt4k.reflectasm.get().toString())

            dependency(rootBt4k.assertj.core.get().toString())

            dependency(rootBt4k.mockk.get().toString())
            dependency(rootBt4k.datafaker.get().toString())
            dependency("io.github.benas:random-beans:${bt4kVersion("random-beans")}")

            dependency(rootBt4k.jsonpath.v3.get().toString())
            dependency(rootBt4k.jsonassert.v2.get().toString())

            // Redis
            dependency("io.lettuce:lettuce-core:${bt4kVersion("lettuce")}")
            dependency("org.redisson:redisson:${bt4kVersion("redisson")}")
        }
    }

    dependencies {
        val api by configurations
        val testApi by configurations
        val implementation by configurations
        val testImplementation by configurations

        val compileOnly by configurations
        val testCompileOnly by configurations
        val testRuntimeOnly by configurations

        compileOnly(platform(bt4kLibrary("bluetape4k-bom")))
        compileOnly(platform(rootLibs.spring.boot4.dependencies))
        compileOnly(platform(rootBt4k.jackson2.bom))
        compileOnly(platform(rootLibs.kotlinx.coroutines.bom))

        implementation(rootLibs.kotlin.stdlib)
        implementation(rootLibs.kotlin.reflect)
        testImplementation(rootLibs.kotlin.test)
        testImplementation(rootLibs.kotlin.test.junit5)

        implementation(rootLibs.kotlinx.coroutines.core)
        implementation(rootBt4k.kotlinx.atomicfu)

        implementation(bt4kLibrary("slf4j-api"))
        implementation(bt4kLibrary("bluetape4k-logging"))
        implementation(rootBt4k.logback.asProvider())
        testImplementation(rootLibs.jcl.over.slf4j)
        testImplementation(rootLibs.jul.to.slf4j)
        testImplementation(rootLibs.log4j.over.slf4j)

        // JUnit 5
        testImplementation(bt4kLibrary("bluetape4k-junit5"))
        testImplementation(rootBt4k.junit.jupiter.all)
        testRuntimeOnly(rootBt4k.junit.platform.engine)

        testImplementation(rootBt4k.mockk)
        testImplementation(rootLibs.awaitility.kotlin)

        testImplementation(rootBt4k.datafaker)
        testImplementation(rootLibs.random.beans)
    }
}


dependencies {
    val benchmarkProjects = setOf(":benchmarks", ":exposed-jpa-benchmark")

    subprojects
        .filter { it.plugins.hasPlugin("org.jetbrains.kotlin.jvm") && it.path !in benchmarkProjects }
        .forEach { sub ->
            kover(dependencies.project(mapOf("path" to sub.path)))
        }
}
