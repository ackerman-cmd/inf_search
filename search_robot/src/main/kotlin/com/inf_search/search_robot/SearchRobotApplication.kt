package com.inf_search.search_robot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class SearchRobotApplication

fun main(args: Array<String>) {
    runApplication<SearchRobotApplication>(*args)
}
