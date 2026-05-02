configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(libs.bluetape4k.io)

    // Web
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")

    // Webflux
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")

    compileOnly("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "junit", module = "junit")
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(module = "mockito-core")
    }

    // Coroutines
    compileOnly(libs.bluetape4k.coroutines)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)

    // Netty
    testImplementation(libs.bluetape4k.netty)

    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.lib)
}
