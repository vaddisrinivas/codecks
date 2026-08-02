package io.codecks.data.update

import io.codecks.BuildConfig
import io.codecks.domain.update.ManualUpdateCheckRequest
import io.codecks.domain.update.SemanticVersion
import io.codecks.domain.update.UpdateAvailability
import io.codecks.domain.update.UpdateChecker
import io.codecks.domain.update.UpdateSourcePolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val MAX_RESPONSE_BYTES = 512 * 1024
private const val MAX_REDIRECTS = 3

data class UpdateHttpResponse(
    val body: String,
    val finalUrl: String,
)

fun interface UpdateHttpTransport {
    suspend fun get(url: String, sourcePolicy: UpdateSourcePolicy): Result<UpdateHttpResponse>
}

class GitHubUpdateRepository(
    private val transport: UpdateHttpTransport = HttpsUpdateHttpTransport(),
    private val sourcePolicy: UpdateSourcePolicy = UpdateSourcePolicy(
        releasesApiUrl = BuildConfig.GITHUB_RELEASES_API_URL,
        apiHosts = setOf(BuildConfig.GITHUB_API_HOST),
        releasePageHosts = setOf(BuildConfig.GITHUB_RELEASE_HOST),
    ),
    private val appForeground: () -> Boolean = { false },
) : UpdateChecker {
    override suspend fun check(
        request: ManualUpdateCheckRequest,
        currentVersionName: String,
    ): Result<UpdateAvailability> = runCatching {
        require(appForeground()) { "Update checks require the app foreground" }
        require(request.requestedAtMillis >= 0L)
        val current = SemanticVersion.parse(currentVersionName).getOrThrow()
        val response = transport.get(sourcePolicy.releasesApiUrl, sourcePolicy).getOrThrow()
        sourcePolicy.requireApiUrl(response.finalUrl)
        val stableReleases = JSONArray(response.body)
            .let { releases ->
                List(releases.length()) { releases.getJSONObject(it) }
            }
            .filterNot { it.optBoolean("draft", false) || it.optBoolean("prerelease", false) }
            .mapNotNull { release ->
                val version = SemanticVersion.parse(release.optString("tag_name")).getOrNull()
                    ?: return@mapNotNull null
                val releasePageUrl = release.optString("html_url")
                sourcePolicy.requireReleasePageUrl(releasePageUrl)
                StableRelease(version, releasePageUrl)
            }
        val latest = stableReleases.maxByOrNull(StableRelease::version)
            ?: error("GitHub returned no valid stable release")
        if (latest.version > current) {
            UpdateAvailability.Available(
                currentVersion = current,
                latestVersion = latest.version,
                releasePageUrl = latest.releasePageUrl,
            )
        } else {
            UpdateAvailability.UpToDate(
                currentVersion = current,
                latestVersion = latest.version,
            )
        }
    }

    private data class StableRelease(
        val version: SemanticVersion,
        val releasePageUrl: String,
    )
}

class HttpsUpdateHttpTransport : UpdateHttpTransport {
    override suspend fun get(
        url: String,
        sourcePolicy: UpdateSourcePolicy,
    ): Result<UpdateHttpResponse> = withContext(Dispatchers.IO) {
        runCatching {
            var current = sourcePolicy.requireApiUrl(url)
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                val connection = (URL(current.toString()).openConnection() as HttpsURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Codecks-Android")
                }
                try {
                    val status = connection.responseCode
                    if (status in 300..399) {
                        require(redirectCount < MAX_REDIRECTS) { "Too many update redirects" }
                        val location = connection.getHeaderField("Location")
                            ?: error("Update redirect has no location")
                        current = sourcePolicy.requireApiUrl(current.resolve(location).toString())
                        return@repeat
                    }
                    require(status == HttpURLConnection.HTTP_OK) { "Update check failed with HTTP $status" }
                    val bytes = connection.inputStream.use(::readBounded)
                    require(bytes.size <= MAX_RESPONSE_BYTES) { "Update metadata response is too large" }
                    return@runCatching UpdateHttpResponse(bytes.toString(Charsets.UTF_8), current.toString())
                } finally {
                    connection.disconnect()
                }
            }
            error("Too many update redirects")
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (output.size() <= MAX_RESPONSE_BYTES) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
