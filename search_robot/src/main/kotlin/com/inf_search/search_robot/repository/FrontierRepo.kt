package com.inf_search.search_robot.repository

import com.inf_search.search_robot.entity.FrontierEntity
import com.inf_search.search_robot.entity.task.TaskKind
import com.inf_search.search_robot.entity.task.TaskStatus
import org.springframework.data.mongodb.repository.MongoRepository

interface FrontierRepo : MongoRepository<FrontierEntity, String> {

    fun findByStatusAndUpdatedAtLessThan(status: TaskStatus, threshold: Long): List<FrontierEntity>

    fun existsBySource(source: String): Boolean
    fun findBySourceAndUrlNormAndKind(source: String, urlNorm: String, kind: TaskKind): FrontierEntity?

    fun countByKindAndStatus(kind: TaskKind, status: TaskStatus): Long
    fun findFirstBySourceAndUrlNormAndKind(source: String, urlNorm: String, kind: TaskKind): FrontierEntity?

}
