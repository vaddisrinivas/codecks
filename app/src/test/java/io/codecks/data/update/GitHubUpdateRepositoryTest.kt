package io.codecks.data.update

import io.codecks.domain.update.ManualUpdateCheckRequest
import io.codecks.domain.update.UpdateAvailability
import io.codecks.domain.update.UpdateSourcePolicy
import io.codecks.domain.update.UpdateCheckNotForegroundException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateRepositoryTest {
    private val policy = UpdateSourcePolicy(
        releasesApiUrl = "https://api.github.com/repos/example/codecks/releases",
        apiHosts = setOf("api.github.com"),
        releasePageHosts = setOf("github.com"),
    )

    @Test
    fun stableCheckIgnoresDraftsPrereleasesAndMalformedTags() = runTest {
        val repository = repository(
            """
            [
              {"tag_name":"v1.3.0","draft":true,"prerelease":false,"html_url":"https://github.com/example/codecks/releases/tag/v1.3.0"},
              {"tag_name":"v1.2.0-rc.1","draft":false,"prerelease":true,"html_url":"https://github.com/example/codecks/releases/tag/v1.2.0-rc.1"},
              {"tag_name":"latest","draft":false,"prerelease":false,"html_url":"https://github.com/example/codecks/releases/tag/latest"},
              {"tag_name":"v1.2.0","draft":false,"prerelease":false,"html_url":"https://github.com/example/codecks/releases/tag/v1.2.0"}
            ]
            """.trimIndent(),
        )

        val result = repository.check(request(), "1.1.0").getOrThrow()

        assertTrue(result is UpdateAvailability.Available)
        assertEquals(2, (result as UpdateAvailability.Available).latestVersion.minor)
    }

    @Test
    fun currentOrNewerInstalledVersionIsUpToDate() = runTest {
        val repository = repository(stableRelease("v1.2.0"))

        assertTrue(repository.check(request(), "1.2.0").getOrThrow() is UpdateAvailability.UpToDate)
        assertTrue(repository.check(request(), "1.3.0").getOrThrow() is UpdateAvailability.UpToDate)
    }

    @Test
    fun returnedOffHostReleasePageFailsClosed() = runTest {
        val repository = repository(stableRelease("v1.2.0", "https://evil.example/release"))

        assertTrue(repository.check(request(), "1.0.0").isFailure)
    }

    @Test
    fun returnedSameHostWrongRepositoryPathFailsClosed() = runTest {
        val repository = repository(
            stableRelease("v1.2.0", "https://github.com/another/codecks/releases/tag/v1.2.0"),
        )

        assertTrue(repository.check(request(), "1.0.0").isFailure)
    }

    @Test
    fun malformedOlderReleaseDoesNotHideLatestValidRelease() = runTest {
        val repository = repository(
            """
            [
              {"tag_name":"v1.1.0","draft":false,"prerelease":false,"html_url":"not a URL"},
              {"tag_name":"v1.2.0","draft":false,"prerelease":false,"html_url":"https://github.com/example/codecks/releases/tag/v1.2.0"}
            ]
            """.trimIndent(),
        )

        assertTrue(repository.check(request(), "1.0.0").getOrThrow() is UpdateAvailability.Available)
    }

    @Test
    fun invalidNewestReleaseFailsInsteadOfClaimingOlderReleaseIsLatest() = runTest {
        val repository = repository(
            """
            [
              {"tag_name":"v1.2.0","draft":false,"prerelease":false,"html_url":"https://github.com/example/codecks/releases/tag/v1.2.0"},
              {"tag_name":"v1.3.0","draft":false,"prerelease":false,"html_url":"https://github.com/another/codecks/releases/tag/v1.3.0"}
            ]
            """.trimIndent(),
        )

        assertTrue(repository.check(request(), "1.0.0").isFailure)
    }

    @Test
    fun offHostRedirectFinalUrlFailsClosed() = runTest {
        val transport = UpdateHttpTransport { _, _, _ ->
            Result.success(UpdateHttpResponse(stableRelease("v1.2.0"), "https://evil.example/releases"))
        }

        assertTrue(GitHubUpdateRepository(transport, policy) { true }.check(request(), "1.0.0").isFailure)
    }

    @Test
    fun requestRequiresForegroundUserAction() {
        assertTrue(
            ManualUpdateCheckRequest.fromExplicitForegroundAction(
                appForeground = false,
                requestedAtMillis = 1,
            ).isFailure,
        )
    }

    @Test
    fun repositoryRechecksActualForegroundBeforeTransport() = runTest {
        var transportCalled = false
        val transport = UpdateHttpTransport { url, _, _ ->
            transportCalled = true
            Result.success(UpdateHttpResponse(stableRelease("v1.2.0"), url))
        }

        val result = GitHubUpdateRepository(transport, policy) { false }.check(request(), "1.0.0")

        assertTrue(result.isFailure)
        assertTrue(!transportCalled)
    }

    @Test
    fun repositoryRechecksForegroundAfterTransport() = runTest {
        var foreground = true
        val transport = UpdateHttpTransport { url, _, _ ->
            foreground = false
            Result.success(UpdateHttpResponse(stableRelease("v1.2.0"), url))
        }

        val result = GitHubUpdateRepository(transport, policy) { foreground }.check(request(), "1.0.0")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateCheckNotForegroundException)
    }

    @Test
    fun blockingHttpCallIsDisconnectedWhenAppLeavesForeground() = runBlocking {
        val foreground = AtomicBoolean(true)
        val entered = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val connection = object : HttpsURLConnection(URL(policy.releasesApiUrl)) {
            override fun connect() = Unit
            override fun disconnect() {
                // Simulate the app resuming before the interrupted HTTP stack throws.
                foreground.set(true)
                disconnected.countDown()
            }
            override fun usingProxy(): Boolean = false
            override fun getCipherSuite(): String = "test"
            override fun getLocalCertificates(): Array<Certificate>? = null
            override fun getServerCertificates(): Array<Certificate> = emptyArray()
            override fun getResponseCode(): Int {
                entered.countDown()
                disconnected.await()
                throw java.io.IOException("disconnected")
            }
        }
        val transport = HttpsUpdateHttpTransport { connection }
        val result = async {
            transport.get(policy.releasesApiUrl, policy) { foreground.get() }
        }
        withTimeout(2_000L) {
            while (entered.count > 0L) kotlinx.coroutines.yield()
            foreground.set(false)
            assertTrue(result.await().exceptionOrNull() is UpdateCheckNotForegroundException)
            assertTrue(foreground.get())
        }
    }

    private fun repository(body: String): GitHubUpdateRepository {
        val transport = UpdateHttpTransport { url, _, _ ->
            Result.success(UpdateHttpResponse(body, url))
        }
        return GitHubUpdateRepository(transport, policy) { true }
    }

    private fun request(): ManualUpdateCheckRequest =
        ManualUpdateCheckRequest.fromExplicitForegroundAction(true, 1).getOrThrow()

    private fun stableRelease(
        tag: String,
        page: String = "https://github.com/example/codecks/releases/tag/$tag",
    ): String =
        """[{"tag_name":"$tag","draft":false,"prerelease":false,"html_url":"$page"}]"""
}
