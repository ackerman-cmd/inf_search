package com.inf_search.search_robot.entity

import com.inf_search.search_robot.entity.task.TaskKind
import com.inf_search.search_robot.entity.task.TaskStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "frontier")
@CompoundIndex(
    name = "uniq_source_url_kind",
    def = "{'source': 1, 'urlNorm': 1, 'kind': 1}",
    unique = true
)
data class FrontierEntity(
    @Id var id: String? = null,
    var source: String,
    var urlNorm: String,
    var urlRaw: String,
    var kind: TaskKind = TaskKind.PAGE,
    @Indexed var status: TaskStatus = TaskStatus.PENDING,
    @Indexed var nextDueAt: Long? = null, // Время следующей попытки
    var updatedAt: Long,
    var lastError: String? = null,
    var retryCount: Int = 0
)
