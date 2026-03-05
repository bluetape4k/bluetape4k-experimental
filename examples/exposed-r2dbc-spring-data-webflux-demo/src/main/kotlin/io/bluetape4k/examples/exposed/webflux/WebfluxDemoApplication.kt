package io.bluetape4k.examples.exposed.webflux

import io.bluetape4k.spring.data.exposed.r2dbc.config.EnableCoroutineExposedRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableCoroutineExposedRepositories(
    basePackages = ["io.bluetape4k.examples.exposed.webflux.repository"]
)
class WebfluxDemoApplication

fun main(args: Array<String>) {
    runApplication<WebfluxDemoApplication>(*args)
}
