package io.bluetape4k.examples.exposed.webflux

import io.bluetape4k.spring.data.exposed.r2dbc.config.EnableSuspendExposedRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableSuspendExposedRepositories(
    basePackages = ["io.bluetape4k.examples.exposed.webflux.repository"]
)
class WebfluxDemoApplication

fun main(args: Array<String>) {
    runApplication<WebfluxDemoApplication>(*args)
}
