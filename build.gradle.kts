import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    base
    alias(libs.plugins.kotlin.jvm)

    // see: https://kotlinlang.org/docs/reference/compiler-plugins.html
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlin.noarg) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.atomicfu)

    alias(libs.plugins.detekt)

    alias(libs.plugins.dependency.management)
    alias(libs.plugins.spring.boot4) apply false

    alias(libs.plugins.dokka)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.gatling) apply false
}

val rootLibs = libs

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
    apply {
        plugin<JavaLibraryPlugin>()

        plugin("org.jetbrains.kotlin.jvm")

        // Atomicfu
        plugin("org.jetbrains.kotlinx.atomicfu")

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
            languageVersion.set(KotlinVersion.KOTLIN_2_3)
            apiVersion.set(KotlinVersion.KOTLIN_2_3)
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-jvm-default=enable",
                // "-Xinline-classes",   // Kotlin 2.+ 에서는 불필요
                "-Xstring-concat=indy",
                "-Xcontext-parameters",
                "-Xannotation-default-target=param-property",
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
            finalizedBy(reportMerge)
            reportMerge.configure {
                input.from(this@detekt.xmlReportFile)
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
            mavenBom(rootLibs.bluetape4k.bom.get().toString())
            mavenBom(rootLibs.spring.boot4.dependencies.get().toString())

            mavenBom(rootLibs.feign.bom.get().toString())
            mavenBom(rootLibs.micrometer.bom.get().toString())
            mavenBom(rootLibs.micrometer.tracing.bom.get().toString())
            mavenBom(rootLibs.opentelemetry.bom.get().toString())
            mavenBom(rootLibs.log4j.bom.get().toString())
            mavenBom(rootLibs.testcontainers.bom.get().toString())
            mavenBom(rootLibs.junit.bom.get().toString())
            mavenBom(rootLibs.okhttp3.bom.get().toString())
            mavenBom(rootLibs.netty.bom.get().toString())
            mavenBom(rootLibs.jackson.bom.get().toString())
            mavenBom(rootLibs.jackson3.bom.get().toString())

            mavenBom(rootLibs.kotlinx.coroutines.bom.get().toString())
            mavenBom(rootLibs.kotlin.bom.get().toString())
        }
        dependencies {
            // Versions pinned explicitly (not managed by any imported BOM)
            dependency(rootLibs.jetbrains.annotations.get().toString())

            // Apache Commons
            dependency(rootLibs.commons.beanutils.get().toString())
            dependency(rootLibs.commons.collections4.get().toString())
            dependency(rootLibs.commons.compress.get().toString())
            dependency(rootLibs.commons.codec.get().toString())
            dependency(rootLibs.commons.csv.get().toString())
            dependency(rootLibs.commons.lang3.get().toString())
            dependency(rootLibs.commons.logging.get().toString())
            dependency(rootLibs.commons.math3.get().toString())
            dependency(rootLibs.commons.pool2.get().toString())
            dependency(rootLibs.commons.text.get().toString())
            dependency(rootLibs.commons.exec.get().toString())
            dependency(rootLibs.commons.io.get().toString())

            // Logging
            dependency(rootLibs.logback.classic.get().toString())
            dependency(rootLibs.logback.core.get().toString())

            // jakarta
            dependency(rootLibs.jakarta.activation.api.get().toString())
            dependency(rootLibs.jakarta.annotation.api.get().toString())
            dependency(rootLibs.jakarta.el.api.get().toString())
            dependency(rootLibs.jakarta.inject.api.get().toString())
            dependency(rootLibs.jakarta.interceptor.api.get().toString())
            dependency(rootLibs.jakarta.jms.api.get().toString())
            dependency(rootLibs.jakarta.json.api.get().toString())
            dependency(rootLibs.jakarta.json.glassfish.get().toString())
            dependency(rootLibs.jakarta.persistence.api.get().toString())
            dependency(rootLibs.jakarta.servlet.api.get().toString())
            dependency(rootLibs.jakarta.transaction.api.get().toString())
            dependency(rootLibs.jakarta.validation.api.get().toString())
            dependency(rootLibs.jakarta.ws.rs.api.get().toString())
            dependency(rootLibs.jakarta.xml.bind.get().toString())

            // Compressor
            dependency(rootLibs.snappy.java.get().toString())
            dependency(rootLibs.lz4.java.get().toString())
            dependency(rootLibs.zstd.jni.get().toString())

            dependency(rootLibs.findbugs.get().toString())
            dependency(rootLibs.guava.get().toString())

            dependency(rootLibs.kryo5.get().toString())
            dependency(rootLibs.fory.kotlin.get().toString())

            dependency(rootLibs.caffeine.lib.get().toString())
            dependency(rootLibs.caffeine.jcache.get().toString())

            dependency(rootLibs.objenesis.get().toString())
            dependency(rootLibs.ow2.asm.get().toString())

            dependency(rootLibs.reflectasm.get().toString())

            dependency(rootLibs.kluent.get().toString())
            dependency(rootLibs.assertj.core.get().toString())

            dependency(rootLibs.mockk.get().toString())
            dependency(rootLibs.datafaker.get().toString())
            dependency(rootLibs.random.beans.get().toString())

            dependency(rootLibs.jsonpath.get().toString())
            dependency(rootLibs.jsonassert.get().toString())

            // Redis
            dependency(rootLibs.lettuce.core.get().toString())
            dependency(rootLibs.redisson.get().toString())
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

        compileOnly(platform(rootLibs.bluetape4k.bom))
        compileOnly(platform(rootLibs.spring.boot4.dependencies))
        compileOnly(platform(rootLibs.jackson.bom))
        compileOnly(platform(rootLibs.kotlinx.coroutines.bom))

        implementation(rootLibs.kotlin.stdlib)
        implementation(rootLibs.kotlin.reflect)
        testImplementation(rootLibs.kotlin.test)
        testImplementation(rootLibs.kotlin.test.junit5)

        implementation(rootLibs.kotlinx.coroutines.core)
        implementation(rootLibs.kotlinx.atomicfu)

        implementation(rootLibs.slf4j.api)
        implementation(rootLibs.bluetape4k.logging)
        implementation(rootLibs.logback.classic)
        testImplementation(rootLibs.jcl.over.slf4j)
        testImplementation(rootLibs.jul.to.slf4j)
        testImplementation(rootLibs.log4j.over.slf4j)

        // JUnit 5
        testImplementation(rootLibs.bluetape4k.junit5)
        testImplementation(rootLibs.junit.jupiter.all)
        testRuntimeOnly(rootLibs.junit.platform.engine)

        testImplementation(rootLibs.kluent)
        testImplementation(rootLibs.mockk)
        testImplementation(rootLibs.awaitility.kotlin)

        testImplementation(rootLibs.datafaker)
        testImplementation(rootLibs.random.beans)
    }
}
