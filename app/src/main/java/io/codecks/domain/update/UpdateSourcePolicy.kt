package io.codecks.domain.update

import java.net.URI

data class UpdateSourcePolicy(
    val releasesApiUrl: String,
    val apiHosts: Set<String>,
    val releasePageHosts: Set<String>,
) {
    init {
        requireApiUrl(releasesApiUrl)
    }

    fun requireApiUrl(value: String): URI = requireHttpsUrl(value, apiHosts)

    fun requireReleasePageUrl(value: String): URI = requireHttpsUrl(value, releasePageHosts)

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
