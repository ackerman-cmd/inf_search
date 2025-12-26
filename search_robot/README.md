# Search Robot

Многофункциональный поисковый робот для сбора и анализа документов с различных сайтов. Проект реализован на Kotlin с использованием Spring Boot и MongoDB, поддерживает многопоточную обработку, гибкую конфигурацию источников и resilient-архитектуру.

## Основные возможности
- Многопоточный обход сайтов (crawler/discoverer + fetcher pipeline)
- Гибкая настройка источников, паттернов, ограничений и пагинации через конфиг
- Поддержка MongoDB для хранения документов и frontier-задач
- Расширяемая архитектура (Spring Boot, DI, отдельные компоненты для fetch, frontier, scheduler)
- Поддержка докеризации и быстрого старта

## Быстрый старт
1. Запустите MongoDB через Docker Compose:
	```bash
	docker-compose up -d
	```
	Это поднимет контейнер с MongoDB на порту 27017.

2. Запустите поискового робота:
	```bash
	./gradlew run
	```
	По умолчанию робот стартует с настройками из application.yaml и начнёт обходить сайты, указанные в конфиге.

## Архитектура и компоненты

- **src/main/kotlin/com/inf_search/search_robot/SearchRobotApplication.kt** — точка входа, Spring Boot приложение.
- **robot/CrawlRobot.kt** — основной crawler: реализует pipeline discoverer/fetcher, поддерживает resilient-режим, обработку ошибок, повторные попытки, статистику.
- **robot/FrontierTaskClaimer.kt** — выдача задач из frontier (очереди ссылок).
- **repository/DocumentRepo.kt, FrontierRepo.kt** — доступ к коллекциям MongoDB (документы, frontier-задачи).
- **entity/** — модели документов, задач, статусов, типов задач.
- **net/HttpFetcher.kt** — HTTP-клиент с поддержкой etag/last-modified, повторов, таймаутов.
- **scheduler/DumpScheduler.kt** — периодический дамп/бэкап коллекций.
- **util/** — утилиты для нормализации URL, хеширования, парсинга страниц.
- **config/RobotConfig.kt** — конфигурация источников, паттернов, лимитов, пагинации.

## Как это работает
1. **Инициализация**: при запуске робот читает конфиг, инициализирует frontier (очередь ссылок) и пул потоков.
2. **Discoverer**: отдельные потоки ищут новые ссылки на страницах, добавляют их в frontier.
3. **Fetcher**: другие потоки скачивают контент документов, сохраняют в MongoDB, поддерживают recrawl.
4. **Resilience**: задачи с ошибками получают экспоненциальный backoff, после max попыток помечаются как failed.
5. **Статистика**: периодически выводится статус очереди, количество обработанных документов, ошибок.

## Конфигурация
Все параметры (источники, паттерны, лимиты, пагинация) задаются в конфиге (обычно application.yaml или application.properties).

## Зависимости
- Java 17+
- Kotlin, Spring Boot 4+
- MongoDB (docker-compose.yaml)
- OkHttp, Jsoup (парсинг и HTTP)


## Пример структуры исходников

```
src/main/kotlin/com/inf_search/search_robot/
  |-- SearchRobotApplication.kt
  |-- config/
  |-- dto/
  |-- entity/
  |-- net/
  |-- repository/
  |-- robot/
  |-- scheduler/
  |-- util/
```