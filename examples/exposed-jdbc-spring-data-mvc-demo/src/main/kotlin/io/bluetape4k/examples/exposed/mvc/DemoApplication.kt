package io.bluetape4k.examples.exposed.mvc

import io.bluetape4k.spring.data.exposed.jdbc.repository.config.EnableExposedRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableExposedRepositories(basePackages = ["io.bluetape4k.examples.exposed.mvc.repository"])
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
