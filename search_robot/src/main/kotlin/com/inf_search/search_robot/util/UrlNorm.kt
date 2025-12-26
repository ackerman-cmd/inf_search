package com.inf_search.search_robot.util

import java.net.URI

object UrlNorm {
    fun normalize(url: String): String {
        var u = url.trim()
        u = u.substringBefore('#') // фрагменты не нужны

        // scheme-relative
        if (u.startsWith("//")) u = "https:$u"

        val uri = URI(u)
        val scheme = (uri.scheme ?: "https").lowercase()
        val host = (uri.host ?: "").lowercase()
        val port = uri.port
        val path = if (uri.path.isNullOrBlank()) "/" else uri.path
        val query = uri.query

        val portPart =
            if (port == -1 || (scheme == "https" && port == 443) || (scheme == "http" && port == 80)) "" else ":$port"
        val qPart = if (query.isNullOrBlank()) "" else "?$query"

        return "$scheme://$host$portPart$path$qPart"
    }
}
