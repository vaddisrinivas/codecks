package io.codecks.domain.update

import java.net.URI

data class UpdateSourcePolicy(
    val releasesApiUrl: String,
    val apiHosts: Set<String>,
    val releasePageHosts: Set<String>,
    val repositoryOwner: String = repositoryCoordinates(releasesApiUrl).first,
    val repositoryName: String = repositoryCoordinates(releasesApiUrl).second,
) {
    init {
        require(repositoryOwner.matches(Regex("[A-Za-z0-9_.-]+")))
        require(repositoryName.matches(Regex("[A-Za-z0-9_.-]+")))
        requireApiUrl(releasesApiUrl)
    }

    fun requireApiUrl(value: String): URI = requireHttpsUrl(value, apiHosts).also { uri ->
        require(uri.rawPath == "/repos/$repositoryOwner/$repositoryName/releases") {
            "Update API path is not the configured repository"
        }
        require(uri.rawQuery == null) { "Update API URL cannot contain a query" }
    }

    fun requireReleasePageUrl(value: String): URI = requireHttpsUrl(value, releasePageHosts).also { uri ->
        val prefix = "/$repositoryOwner/$repositoryName/releases/tag/"
        require(uri == uri.normalize()) { "Release URL path is not normalized" }
        require(uri.rawPath.startsWith(prefix) && uri.rawPath.length > prefix.length) {
            "Release URL path is not the configured repository"
        }
        val rawTag = uri.rawPath.removePrefix(prefix)
        require('/' !in rawTag && '\\' !in rawTag) { "Release URL must contain one tag segment" }
        require(!Regex("(?i)%2f|%5c|%2e").containsMatchIn(rawTag)) {
            "Release URL tag contains blocked encoded path characters"
        }
        require(uri.rawQuery == null) { "Release URL cannot contain a query" }
    }

    private fun requireHttpsUrl(value: String, allowedHosts: Set<String>): URI {
        val uri = runCatching { URI(value) }.getOrElse { error("Malformed update URL") }
        val host = uri.host?.lowercase() ?: error("Update URL has no host")
        require(uri.scheme.equals("https", ignoreCase = true)) { "Update URL must use HTTPS" }
        require(uri.rawUserInfo == null) { "Update URL cannot contain user information" }
        require(uri.port == -1 || uri.port == 443) { "Update URL uses a blocked port" }
        require(uri.fragment == null) { "Update URL cannot contain a fragment" }
        require(host in allowedHosts.map(String::lowercase)) { "Update URL host is not allowlisted" }
        return uri
    }
}

private fun repositoryCoordinates(releasesApiUrl: String): Pair<String, String> {
    val segments = runCatching { URI(releasesApiUrl).path.split('/').filter(String::isNotBlank) }
        .getOrElse { emptyList() }
    require(segments.size == 4 && segments[0] == "repos" && segments[3] == "releases") {
        "Update API URL must identify one repository releases endpoint"
    }
    return segments[1] to segments[2]
}
