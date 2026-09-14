package com.example.lib4gz

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class Lib4gzApplication

fun main(args: Array<String>) {
	runApplication<Lib4gzApplication>(*args)
}
