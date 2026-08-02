package io.codecks.data.update

import io.codecks.BuildConfig
import io.codecks.domain.update.ManualUpdateCheckRequest
import io.codecks.domain.update.SemanticVersion
import io.codecks.domain.update.UpdateAvailability
import io.codecks.domain.update.UpdateChecker
import io.codecks.domain.update.UpdateCheckNotForegroundException
import io.codecks.domain.update.UpdateSourcePolicy
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray

private const val MAX_RESPONSE_BYTES = 512 * 1024
private const val MAX_REDIRECTS = 3

data class UpdateHttpResponse(
    val body: String,
    val finalUrl: String,
)

fun interface UpdateHttpTransport {
    suspend fun get(
        url: String,
        sourcePolicy: UpdateSourcePolicy,
        requestActive: () -> Boolean,
    ): Result<UpdateHttpResponse>
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
    ): Result<UpdateAvailability> = try {
        if (!appForeground()) throw UpdateCheckNotForegroundException()
        require(request.requestedAtMillis >= 0L)
        val current = SemanticVersion.parse(currentVersionName).getOrThrow()
        val response = transport.get(sourcePolicy.releasesApiUrl, sourcePolicy, appForeground).getOrThrow()
        if (!appForeground()) throw UpdateCheckNotForegroundException()
        sourcePolicy.requireApiUrl(response.finalUrl)
        val stableReleases = JSONArray(response.body)
            .let { releases ->
                List(releases.length()) { releases.getJSONObject(it) }
            }
            .filterNot { it.optBoolean("draft", false) || it.optBoolean("prerelease", false) }
            .mapNotNull { release ->
                SemanticVersion.parse(release.optString("tag_name")).getOrNull()?.let { version ->
                    StableRelease(version, release.optString("html_url"))
                }
            }
        val latest = stableReleases.maxByOrNull(StableRelease::version)
            ?: error("GitHub returned no valid stable release")
        val validatedLatest = latest.copy(
            releasePageUrl = sourcePolicy.requireReleasePageUrl(latest.releasePageUrl).toString(),
        )
        Result.success(if (latest.version > current) {
            UpdateAvailability.Available(
                currentVersion = current,
                latestVersion = validatedLatest.version,
                releasePageUrl = validatedLatest.releasePageUrl,
            )
        } else {
            UpdateAvailability.UpToDate(
                currentVersion = current,
                latestVersion = latest.version,
            )
        })
    } catch (error: Throwable) {
        error.rethrowIfCancellationOrFatal()
        Result.failure(error)
    }

    private data class StableRelease(
        val version: SemanticVersion,
        val releasePageUrl: String,
    )
}

class HttpsUpdateHttpTransport(
    private val connectionFactory: (URL) -> HttpsURLConnection = {
        it.openConnection() as HttpsURLConnection
    },
) : UpdateHttpTransport {
    override suspend fun get(
        url: String,
        sourcePolicy: UpdateSourcePolicy,
        requestActive: () -> Boolean,
    ): Result<UpdateHttpResponse> {
        val foregroundLost = AtomicBoolean(false)
        return try {
            val response = coroutineScope {
                val activeConnection = AtomicReference<HttpsURLConnection?>()
                val watcher = launch(Dispatchers.IO) {
                    while (isActive) {
                        if (!requestActive()) {
                            foregroundLost.set(true)
                            activeConnection.get()?.disconnect()
                            return@launch
                        }
                        delay(100L)
                    }
                }
                try {
                    runInterruptible(Dispatchers.IO) {
                        ensureForeground(requestActive, foregroundLost)
                        var current = sourcePolicy.requireApiUrl(url)
                        repeat(MAX_REDIRECTS + 1) { redirectCount ->
                            val connection = connectionFactory(URL(current.toString())).apply {
                                instanceFollowRedirects = false
                                connectTimeout = 10_000
                                readTimeout = 10_000
                                requestMethod = "GET"
                                setRequestProperty("Accept", "application/vnd.github+json")
                                setRequestProperty("User-Agent", "Codecks-Android")
                            }
                            activeConnection.set(connection)
                            try {
                                ensureForeground(requestActive, foregroundLost)
                                val status = connection.responseCode
                                ensureForeground(requestActive, foregroundLost)
                                if (status in 300..399) {
                                    require(redirectCount < MAX_REDIRECTS) { "Too many update redirects" }
                                    val location = connection.getHeaderField("Location")
                                        ?: error("Update redirect has no location")
                                    current = sourcePolicy.requireApiUrl(current.resolve(location).toString())
                                    return@repeat
                                }
                                require(status == HttpURLConnection.HTTP_OK) {
                                    "Update check failed with HTTP $status"
                                }
                                val bytes = connection.inputStream.use {
                                    readBounded(it, requestActive) { foregroundLost.set(true) }
                                }
                                require(bytes.size <= MAX_RESPONSE_BYTES) {
                                    "Update metadata response is too large"
                                }
                                return@runInterruptible UpdateHttpResponse(
                                    bytes.toString(Charsets.UTF_8),
                                    current.toString(),
                                )
                            } finally {
                                activeConnection.compareAndSet(connection, null)
                                connection.disconnect()
                            }
                        }
                        error("Too many update redirects")
                    }
                } finally {
                    watcher.cancel()
                    activeConnection.getAndSet(null)?.disconnect()
                }
            }
            ensureForeground(requestActive, foregroundLost)
            Result.success(response)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (foregroundLost.get() || !requestActive()) {
                return Result.failure(UpdateCheckNotForegroundException())
            }
            error.rethrowIfCancellationOrFatal()
            Result.failure(error)
        }
    }

    private fun ensureForeground(
        requestActive: () -> Boolean,
        foregroundLost: AtomicBoolean,
    ) {
        if (!requestActive()) {
            foregroundLost.set(true)
            throw UpdateCheckNotForegroundException()
        }
    }

    private fun readBounded(
        input: InputStream,
        requestActive: () -> Boolean,
        onForegroundLost: () -> Unit,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (output.size() <= MAX_RESPONSE_BYTES) {
            if (!requestActive()) {
                onForegroundLost()
                throw UpdateCheckNotForegroundException()
            }
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

private fun Throwable.rethrowIfCancellationOrFatal() {
    when (this) {
        is CancellationException,
        is VirtualMachineError,
        is ThreadDeath,
        is LinkageError,
        -> throw this
    }
}
