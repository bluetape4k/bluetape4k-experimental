package io.bluetape4k.examples.exposed.webflux

import io.bluetape4k.spring.data.exposed.r2dbc.repository.config.EnableExposedSuspendRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
@EnableExposedSuspendRepositories(
    basePackages = ["io.bluetape4k.examples.exposed.webflux.repository"]
)
class WebfluxDemoApplication

fun main(args: Array<String>) {
    runApplication<WebfluxDemoApplication>(*args)
}
