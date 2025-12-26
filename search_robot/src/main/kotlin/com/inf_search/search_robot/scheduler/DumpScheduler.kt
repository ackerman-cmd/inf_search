package com.inf_search.search_robot.scheduler

import com.inf_search.search_robot.repository.DocumentRepo
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DumpScheduler(
    private val documentRepo: DocumentRepo
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val BATCH_SIZE = 1000 // Размер пачки для защиты памяти

    @Scheduled(fixedRate = 3600000)
    fun scheduledDump() {
        println("[Scheduler] Starting automated hourly dump...")
        performDump()
    }

    private fun performDump() {
        val now = LocalDateTime.now()
        val timestamp = now.format(timeFormatter)

        val directory = File("dumps")
        if (!directory.exists()) directory.mkdirs()

        val fileName = "dumps/corpus_dump_$timestamp.txt"
        val file = File(fileName)

        try {
            file.bufferedWriter().use { writer ->
                var pageNumber = 0
                var totalDocsProcessed = 0L
                var hasNextPage = true

                println("Exporting documents to $fileName with custom format...")

                while (hasNextPage) {
                    val pageRequest = PageRequest.of(pageNumber, BATCH_SIZE, Sort.by("id"))
                    val page = documentRepo.findAll(pageRequest)


                    page.content.forEach { doc ->
                        val html = doc.htmlRaw
                        // Берем ID как заголовок (как вы просили)
                        val docId = doc.id

                        if (!html.isNullOrBlank()) {
                            val plainText = Jsoup.parse(html).text()

                            // --- НАЧАЛО ЗАПИСИ В ВАШЕМ ФОРМАТЕ ---
                            writer.write("==DOC_START==\n")
                            writer.write("$docId\n")         // Заголовок (ID)
                            writer.write("==CONTENT_START==\n")
                            writer.write(plainText)          // Текст
                            writer.write("\n")               // Перенос строки после текста
                            writer.write("==DOC_END==\n")
                            writer.write("\n")               // Пустая строка между документами для красоты
                            // --- КОНЕЦ ЗАПИСИ ---
                        }
                    }

                    totalDocsProcessed += page.numberOfElements

                    // Сбрасываем буфер на диск каждые 10 страниц, чтобы очистить память
                    if (pageNumber % 10 == 0) {
                        println("Processed $totalDocsProcessed documents...")
                        writer.flush()
                    }

                    hasNextPage = page.hasNext()
                    pageNumber++
                }

                println("Dump success: Saved $fileName (Total: $totalDocsProcessed docs)")
            }

        } catch (e: Exception) {
            println("FATAL ERROR during dump: ${e.message}")
            e.printStackTrace()
        }
    }
}