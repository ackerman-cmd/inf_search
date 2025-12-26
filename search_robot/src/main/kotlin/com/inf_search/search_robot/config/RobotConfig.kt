package com.inf_search.search_robot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "robot")
data class RobotConfig(
    val db: DbProps,
    val logic: LogicProps,
    val sources: List<SourceProps>
) {
    data class DbProps(
        val uri: String,
        val database: String,
        val documentsCollection: String,
        val frontierCollection: String
    )

    data class LogicProps(
        val requestDelayMs: Long = 2000,
        val recrawlAfterHours: Long = 168,
        val maxPerRun: Int = 200000,
        val maxRetries: Int = 2,
        val connectTimeoutSec: Long = 10,
        val readTimeoutSec: Long = 20,
        val userAgent: String,
        val discoverFirst: Boolean = true,

        val enableRecrawl: Boolean = false,

        val stopWhenTargetsReached: Boolean = false
    )

    data class SeedPaginationProps(
        val template: String,
        val start: Int,
        val end: Int
    )

    data class SourceProps(
        val name: String,
        val allowedHosts: List<String>,
        val seeds: List<String>,
        val targetArticles: Int = 10000,

        val wikiStrictOneCategory: Boolean = false,

        val seedPagination: SeedPaginationProps? = null,

        val pageAllowPatterns: List<String> = emptyList(),
        val articleAllowPatterns: List<String> = emptyList(),
        val denyPatterns: List<String> = emptyList()
    )
}