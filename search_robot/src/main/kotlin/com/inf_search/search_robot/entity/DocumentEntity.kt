package com.inf_search.search_robot.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "documents")
@CompoundIndex(
    name = "uniq_source_url",
    def = "{'source': 1, 'urlNorm': 1}",
    unique = true
)
data class DocumentEntity(
    @Id var id: String? = null,

    var source: String,
    var urlNorm: String,
    var urlRaw: String,

    var htmlRaw: String? = null,

    var fetchedAt: Long = 0L,
    var sha256: String? = null,
    var etag: String? = null,
    var lastModified: String? = null
)
