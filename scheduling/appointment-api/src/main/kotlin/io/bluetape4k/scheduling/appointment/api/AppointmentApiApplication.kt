package io.bluetape4k.scheduling.appointment.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class AppointmentApiApplication

fun main(args: Array<String>) {
    runApplication<AppointmentApiApplication>(*args)
}
