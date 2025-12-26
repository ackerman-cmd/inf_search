package com.inf_search.search_robot.dto

data class FetchResult(
    val code: Int,
    val html: String?,
    val etag: String?,
    val lastModified: String?
)
