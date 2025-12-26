package com.inf_search.search_robot.repository

import com.inf_search.search_robot.entity.DocumentEntity
import org.springframework.data.mongodb.repository.MongoRepository

interface DocumentRepo : MongoRepository<DocumentEntity, String> {
    fun findBySourceAndUrlNorm(source: String, urlNorm: String): DocumentEntity?
    fun countBySource(source: String): Long
}
