package com.inf_search.search_robot.net

import com.inf_search.search_robot.config.RobotConfig
import com.inf_search.search_robot.dto.FetchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration

class HttpFetcher(cfg: RobotConfig.LogicProps) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(cfg.connectTimeoutSec))
        .readTimeout(Duration.ofSeconds(cfg.readTimeoutSec))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val ua = cfg.userAgent

    fun fetch(url: String, etag: String?, lastModified: String?): FetchResult {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")

        if (!etag.isNullOrBlank()) b.header("If-None-Match", etag)
        if (!lastModified.isNullOrBlank()) b.header("If-Modified-Since", lastModified)

        client.newCall(b.build()).execute().use { resp ->
            val newEtag = resp.header("ETag")
            val newLm = resp.header("Last-Modified")

            return when (resp.code) {
                304 -> FetchResult(304, null, newEtag ?: etag, newLm ?: lastModified)
                200 -> FetchResult(200, resp.body?.string(), newEtag, newLm)
                else -> FetchResult(resp.code, null, newEtag, newLm)
            }
        }
    }
}
