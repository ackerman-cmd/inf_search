package com.inf_search.search_robot.util

import org.jsoup.Jsoup

/**
 * Вспомогательный класс для анализа структуры страниц USGS
 * Используется для понимания того, как правильно извлекать ссылки
 */
object USGSPageAnalyzer {

    fun analyzeSearchPage(url: String) {
        println("Analyzing search page: $url")
        try {
            val doc = Jsoup.connect(url).get()
            println("Page title: ${doc.title()}")

            // Ищем ссылки на публикации
            val publicationLinks = doc.select("a[href*=/publication/]")
            println("Found ${publicationLinks.size} publication links")

            publicationLinks.take(5).forEach { link ->
                println("  - ${link.attr("href")} : ${link.text()}")
            }

            // Ищем пагинацию
            val paginationLinks = doc.select("a[href*=?page=]")
            println("Found ${paginationLinks.size} pagination links")

            paginationLinks.take(5).forEach { link ->
                println("  - ${link.attr("href")} : ${link.text()}")
            }

        } catch (e: Exception) {
            println("Error analyzing page: ${e.message}")
        }
    }

    fun analyzePublicationPage(url: String) {
        println("Analyzing publication page: $url")
        try {
            val doc = Jsoup.connect(url).get()
            println("Page title: ${doc.title()}")

            // Ищем ссылку на основной документ
            val mainDocLinks = doc.select("a[title*=Main document link]")
            println("Found ${mainDocLinks.size} main document links")

            mainDocLinks.forEach { link ->
                println("  - ${link.attr("href")} : ${link.text()}")
            }

            // Ищем ссылки на HTML документы
            val htmlLinks = doc.select("a[href$=.html], a[href*=/fs/], a[href*=/sir/], a[href*=/ofr/]")
            println("Found ${htmlLinks.size} potential HTML document links")

            htmlLinks.take(10).forEach { link ->
                println("  - ${link.attr("href")} : ${link.text()}")
            }

        } catch (e: Exception) {
            println("Error analyzing page: ${e.message}")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // Анализируем несколько страниц для понимания структуры
        analyzeSearchPage("https://pubs.usgs.gov/search?q=geology&page=1")
        println()
        analyzePublicationPage("https://pubs.usgs.gov/publication/70250878")
    }
}