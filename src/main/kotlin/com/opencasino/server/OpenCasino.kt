package com.opencasino.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OpenCasino

fun main(args: Array<String>) {
    runApplication<OpenCasino>(*args)
}
