package com.inf_search.search_robot.robot

import com.inf_search.search_robot.config.RobotConfig
import com.inf_search.search_robot.dto.FetchResult
import com.inf_search.search_robot.entity.DocumentEntity
import com.inf_search.search_robot.entity.FrontierEntity
import com.inf_search.search_robot.entity.task.TaskKind
import com.inf_search.search_robot.entity.task.TaskStatus
import com.inf_search.search_robot.net.HttpFetcher
import com.inf_search.search_robot.repository.DocumentRepo
import com.inf_search.search_robot.repository.FrontierRepo
import com.inf_search.search_robot.util.HashUtils
import com.inf_search.search_robot.util.UrlNorm
import org.jsoup.Jsoup
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

@Component
class CrawlRobot(
    private val cfg: RobotConfig,
    private val docs: DocumentRepo,
    private val frontier: FrontierRepo,
    private val claimer: FrontierTaskClaimer
) : CommandLineRunner {

    private val fetcher = HttpFetcher(cfg.logic)
    private val running = AtomicBoolean(true)

    // Пул потоков для параллельной работы
    private val pool = Executors.newFixedThreadPool(8)

    // Счетчики для мониторинга
    private val discoveredCount = AtomicInteger(0)
    private val fetchedCount = AtomicInteger(0)

    data class WikiStrictCategory(
        val host: String,
        val categoryTitle: String,
        val wikiPrefix: String,
        val indexPrefix: String
    )

    data class CompiledSource(
        val name: String,
        val allowedHosts: Set<String>,
        val seeds: List<String>,
        val targetArticles: Long,
        val seedPagination: RobotConfig.SeedPaginationProps?,
        val wikiStrictOneCategory: Boolean,
        val wikiStrict: WikiStrictCategory?,
        val pageAllow: List<Pattern>,
        val articleAllow: List<Pattern>,
        val deny: List<Pattern>
    )

    // Предварительная компиляция конфигов
    private val sources: List<CompiledSource> = cfg.sources.map { s ->
        val strict = if (s.wikiStrictOneCategory) buildWikiStrictFromSeed(s.seeds.firstOrNull()) else null
        CompiledSource(
            name = s.name,
            allowedHosts = s.allowedHosts.map { it.lowercase() }.toSet(),
            seeds = s.seeds,
            targetArticles = s.targetArticles.toLong(),
            seedPagination = s.seedPagination,
            wikiStrictOneCategory = s.wikiStrictOneCategory,
            wikiStrict = strict,
            pageAllow = compilePatterns(s.name, s.pageAllowPatterns, "pageAllowPatterns"),
            articleAllow = compilePatterns(s.name, s.articleAllowPatterns, "articleAllowPatterns"),
            deny = compilePatterns(s.name, s.denyPatterns, "denyPatterns")
        )
    }

    private fun compilePatterns(source: String, patterns: List<String>, kind: String): List<Pattern> =
        patterns.map { p ->
            try {
                Pattern.compile(p)
            } catch (e: Exception) {
                throw IllegalArgumentException("Bad regex in source='$source' ($kind): '$p' -> ${e.message}", e)
            }
        }

    private fun buildWikiStrictFromSeed(seedUrlRaw: String?): WikiStrictCategory? {
        if (seedUrlRaw.isNullOrBlank()) return null
        val seed = try { UrlNorm.normalize(seedUrlRaw) } catch (_: Exception) { return null }

        val uri = try { URI(seed) } catch (_: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        val base = "https://$host"

        val wikiMarker = "/wiki/Category:"
        val idxMarker = "/w/index.php?title=Category:"

        val title: String? = when {
            seed.contains(wikiMarker) -> seed.substringAfter(wikiMarker).substringBefore('?').substringBefore('#')
            seed.contains(idxMarker) -> seed.substringAfter(idxMarker).substringBefore('&').substringBefore('#')
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }

        return WikiStrictCategory(
            host = host,
            categoryTitle = title ?: return null,
            wikiPrefix = "$base/wiki/Category:${title}",
            indexPrefix = "$base/w/index.php?title=Category:${title}"
        )
    }

    override fun run(vararg args: String) {
        println("Robot started (RESILIENCE MODE): Discoverer + Fetcher pipeline with Exponential Backoff.")
        println("requestDelayMs=${cfg.logic.requestDelayMs}, Threads=8")

        resetStuckInProgress(stuckMinutes = 30)
        seedIfNeeded()

        Runtime.getRuntime().addShutdownHook(Thread {
            println("Shutdown requested...")
            running.set(false)
            pool.shutdown()
            try {
                if (!pool.awaitTermination(15, TimeUnit.SECONDS)) {
                    pool.shutdownNow()
                }
            } catch (e: InterruptedException) {
                pool.shutdownNow()
            }
            printFinalStats()
        })

        // Запускаем 4 потока на поиск ссылок и 4 на скачивание контента
        repeat(4) { pool.submit { discoverLoop() } }
        repeat(4) { pool.submit { fetchLoop() } }

        while (running.get()) {
            Thread.sleep(30_000)
            printStats()
        }
    }


    private fun handleTaskError(task: FrontierEntity, e: Exception) {
        val maxRetries = 2
        val now = Instant.now().epochSecond

        if (task.retryCount < maxRetries) {
            val backoffMinutes = Math.pow(2.0, task.retryCount.toDouble()).toLong() * 10
            task.status = TaskStatus.PENDING
            task.nextDueAt = now + (backoffMinutes * 60)
            task.retryCount += 1
            task.lastError = "Retry ${task.retryCount} in ${backoffMinutes}m: ${e.message}"
            println("[retry] source=${task.source} url=${task.urlRaw} attempt=${task.retryCount} scheduled_in=${backoffMinutes}m")
        } else {
            task.status = TaskStatus.FAILED
            task.lastError = "Max retries ($maxRetries) reached: ${e.message}"
            println("[fatal] source=${task.source} url=${task.urlRaw} failed after all retries.")
        }
        task.updatedAt = now
        frontier.save(task)
    }


    private fun discoverLoop() {
        println("[discover] loop started in thread ${Thread.currentThread().name}")
        while (running.get()) {
            val task = claimer.claimNextPending(TaskKind.PAGE)
            if (task == null) {
                Thread.sleep(1000)
                continue
            }
            try {
                handlePageTask(task)
                // Сбрасываем счетчик ошибок при успешном завершении
                task.retryCount = 0
                task.status = TaskStatus.DONE
                task.updatedAt = Instant.now().epochSecond
                task.lastError = null
                frontier.save(task)
                discoveredCount.incrementAndGet()
            } catch (e: Exception) {
                handleTaskError(task, e)
            }
            Thread.sleep(cfg.logic.requestDelayMs / 2)
        }
    }

    private fun fetchLoop() {
        println("[fetch] loop started in thread ${Thread.currentThread().name}")
        val recrawlAfterSec = cfg.logic.recrawlAfterHours * 3600

        while (running.get()) {
            val task = claimer.claimNextPending(TaskKind.ARTICLE)
            if (task == null) {
                Thread.sleep(1000)
                continue
            }
            try {
                handleArticleTask(task, recrawlAfterSec)
                // Сбрасываем счетчик ошибок при успешном завершении
                task.retryCount = 0
                task.status = TaskStatus.DONE
                task.updatedAt = Instant.now().epochSecond
                task.lastError = null
                frontier.save(task)
                fetchedCount.incrementAndGet()
            } catch (e: Exception) {
                handleTaskError(task, e)
            }
            Thread.sleep(cfg.logic.requestDelayMs / 2)
        }
    }


    private fun handlePageTask(task: FrontierEntity) {
        val res = fetchWithRetries(task.urlRaw, etag = null, lastModified = null)
        if (res.code != 200 || res.html.isNullOrBlank()) {
            throw RuntimeException("HTTP ${res.code} (or empty body)")
        }

        val stats = discoverLinks(task.source, task.urlRaw, res.html!!)
        println("[discover] source=${task.source} page=${task.urlRaw} found_articles=${stats.insertedArticles}")
    }

    private fun handleArticleTask(task: FrontierEntity, recrawlAfterSec: Long) {
        val now = Instant.now().epochSecond
        val existing = docs.findBySourceAndUrlNorm(task.source, task.urlNorm)
        val res = fetchWithRetries(task.urlRaw, existing?.etag, existing?.lastModified)

        when (res.code) {
            200 -> {
                val html = res.html ?: ""
                val doc = existing ?: DocumentEntity(
                    source = task.source, urlNorm = task.urlNorm, urlRaw = task.urlRaw
                )
                doc.htmlRaw = html
                doc.fetchedAt = now
                doc.sha256 = HashUtils.sha256(html)
                doc.etag = res.etag
                doc.lastModified = res.lastModified
                docs.save(doc)
            }
            304 -> {
                // Документ актуален
                existing?.let {
                    it.fetchedAt = now
                    docs.save(it)
                }
            }
            else -> throw RuntimeException("HTTP ${res.code}")
        }

        task.nextDueAt = if (cfg.logic.enableRecrawl) now + recrawlAfterSec else null
    }

    data class DiscoverStats(val insertedPages: Int, val insertedArticles: Int)

    private fun discoverLinks(sourceName: String, baseUrlRaw: String, html: String): DiscoverStats {
        val src = sources.firstOrNull { it.name == sourceName } ?: return DiscoverStats(0, 0)
        val baseUrl = try { UrlNorm.normalize(baseUrlRaw) } catch (_: Exception) { baseUrlRaw }
        val doc = Jsoup.parse(html, baseUrl)

        var insPages = 0
        var insArticles = 0

        if (sourceName == "usgs_geology" && baseUrl.contains("/publication/")) {
            val linkElements = doc.select("a[href]")

            linkElements.forEach { el ->
                val text = el.text().lowercase()
                val title = el.attr("title").lowercase()
                val abs = el.absUrl("href")
                if (abs.isBlank()) return@forEach

                val isArticleLink = text.contains("publisher index page") ||
                        text.contains("main document link") ||
                        text.contains("full text (html)") ||
                        text.contains("report (html)") ||
                        title.contains("main document")

                if (isArticleLink) {
                    val href = try { UrlNorm.normalize(abs) } catch (_: Exception) { null }
                    if (href != null) {
                        // Разрешаем DOI ссылки или хосты из белого списка
                        val isDoi = href.contains("doi.org")
                        if (isDoi || isAllowedHost(src, href)) {
                            // Если это DOI или подходит под паттерны статей в конфиге
                            if (isDoi || src.articleAllow.any { it.matcher(href).matches() }) {
                                if (upsertFrontier(sourceName, href, TaskKind.ARTICLE)) insArticles++
                            }
                        }
                    }
                }
            }
            if (insArticles > 0) return DiscoverStats(0, insArticles)
        }

        val strict = src.wikiStrict
        val isStrictWiki = src.wikiStrictOneCategory && strict != null &&
                (baseUrl.startsWith(strict.wikiPrefix) || baseUrl.startsWith(strict.indexPrefix))

        val elements = if (isStrictWiki) doc.select("#mw-pages a[href]") else doc.select("a[href]")

        elements.forEach { a ->
            val abs = a.absUrl("href")
            if (abs.isNullOrBlank()) return@forEach
            val href = try { UrlNorm.normalize(abs) } catch (_: Exception) { return@forEach }

            if (!isAllowedHost(src, href)) return@forEach
            if (src.deny.any { it.matcher(href).matches() }) return@forEach

            if (isStrictWiki && strict != null) {
                val kind = classifyStrictWikiLink(href, strict)
                when (kind) {
                    TaskKind.PAGE -> if (upsertFrontier(sourceName, href, TaskKind.PAGE)) insPages++
                    TaskKind.ARTICLE -> if (upsertFrontier(sourceName, href, TaskKind.ARTICLE)) insArticles++
                    null -> {}
                }
                return@forEach
            }

            val isArticle = src.articleAllow.any { it.matcher(href).matches() }
            val isPage = src.pageAllow.any { it.matcher(href).matches() }

            when {
                isArticle -> if (upsertFrontier(sourceName, href, TaskKind.ARTICLE)) insArticles++
                isPage -> if (upsertFrontier(sourceName, href, TaskKind.PAGE)) insPages++
            }
        }

        return DiscoverStats(insPages, insArticles)
    }

    private fun classifyStrictWikiLink(href: String, strict: WikiStrictCategory): TaskKind? {
        val isSameCategory = href.startsWith(strict.wikiPrefix) || href.startsWith(strict.indexPrefix)
        if (isSameCategory) return TaskKind.PAGE

        val uri = try { URI(href) } catch (_: Exception) { return null }
        if (uri.host?.lowercase() != strict.host) return null
        if (uri.rawQuery != null) return null

        val path = uri.path ?: return null
        if (!path.startsWith("/wiki/")) return null

        val title = path.removePrefix("/wiki/")
        if (title.isBlank() || title.contains(":") || title.startsWith("Category:")) return null

        return TaskKind.ARTICLE
    }

    private fun isAllowedHost(src: CompiledSource, url: String): Boolean {
        return try {
            val host = URI(url).host?.lowercase() ?: return false
            src.allowedHosts.contains(host)
        } catch (_: Exception) {
            false
        }
    }

    private fun upsertFrontier(source: String, urlRaw: String, kind: TaskKind): Boolean {
        val urlNorm = UrlNorm.normalize(urlRaw)
        val existing = frontier.findBySourceAndUrlNormAndKind(source, urlNorm, kind)
        if (existing == null) {
            frontier.save(
                FrontierEntity(
                    source = source, urlNorm = urlNorm, urlRaw = urlRaw,
                    kind = kind, status = TaskStatus.PENDING, updatedAt = Instant.now().epochSecond
                )
            )
            return true
        }
        return false
    }

    private fun fetchWithRetries(url: String, etag: String?, lastModified: String?): FetchResult {
        // Базовая логика повторов (внутри HttpFetcher) вызывается здесь
        return fetcher.fetch(url, etag, lastModified)
    }

    private fun resetStuckInProgress(stuckMinutes: Long) {
        val threshold = Instant.now().epochSecond - stuckMinutes * 60
        val stuck = frontier.findByStatusAndUpdatedAtLessThan(TaskStatus.IN_PROGRESS, threshold)
        if (stuck.isEmpty()) return
        println("Resetting ${stuck.size} stuck tasks (IN_PROGRESS > $stuckMinutes min)...")
        stuck.forEach {
            it.status = TaskStatus.PENDING
            it.updatedAt = Instant.now().epochSecond
        }
        frontier.saveAll(stuck)
    }

    private fun seedIfNeeded() {
        sources.forEach { s ->
            if (!frontier.existsBySource(s.name)) {
                println("Seeding source=${s.name}")
                s.seeds.forEach { upsertFrontier(s.name, it, TaskKind.PAGE) }

                s.seedPagination?.let { sp ->
                    val maxPages = minOf(sp.end, sp.start + 150)
                    for (p in sp.start..maxPages) {
                        val url = sp.template.replace("{page}", p.toString())
                        upsertFrontier(s.name, url, TaskKind.PAGE)
                    }
                }
            }
        }
    }

    private fun printStats() {
        val pendingPages = frontier.countByKindAndStatus(TaskKind.PAGE, TaskStatus.PENDING)
        val pendingArticles = frontier.countByKindAndStatus(TaskKind.ARTICLE, TaskStatus.PENDING)
        val doneArticles = frontier.countByKindAndStatus(TaskKind.ARTICLE, TaskStatus.DONE)

        println("=== ROBOT STATUS ===")
        println("Queue: [Pages: $pendingPages | Articles: $pendingArticles]")
        println("Done: $doneArticles | Discovered: ${discoveredCount.get()} | Fetched: ${fetchedCount.get()}")
        println("====================")
    }

    private fun printFinalStats() {
        println("=== FINAL STATS ===")
        sources.forEach { s ->
            println("Source [${s.name}]: ${docs.countBySource(s.name)} documents collected")
        }
        println("Total Discovered: ${discoveredCount.get()}")
        println("Total Fetched: ${fetchedCount.get()}")
        println("===================")
    }
} }