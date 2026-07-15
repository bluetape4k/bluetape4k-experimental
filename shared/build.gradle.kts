configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    implementation(bt4k.bluetape4k.io)

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
    compileOnly(bt4k.bluetape4k.coroutines)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.coroutines.reactor)
    testImplementation(libs.kotlinx.coroutines.test)

    // Netty
    testImplementation(bt4k.bluetape4k.netty)

    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.lib)
}
