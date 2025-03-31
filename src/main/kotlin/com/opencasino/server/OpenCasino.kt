package com.opencasino.server

import com.opencasino.server.service.auth.impl.YamlPropertySourceFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.PropertySource

@SpringBootApplication
class OpenCasino

fun main(args: Array<String>) {
    runApplication<OpenCasino>(*args)
}
