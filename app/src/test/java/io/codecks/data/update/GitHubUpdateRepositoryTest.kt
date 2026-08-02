package io.codecks.data.update

import io.codecks.domain.update.ManualUpdateCheckRequest
import io.codecks.domain.update.UpdateAvailability
import io.codecks.domain.update.UpdateSourcePolicy
import kotlinx.coroutines.test.runTest
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
    fun offHostRedirectFinalUrlFailsClosed() = runTest {
        val transport = UpdateHttpTransport { _, _ ->
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
        val transport = UpdateHttpTransport { url, _ ->
            transportCalled = true
            Result.success(UpdateHttpResponse(stableRelease("v1.2.0"), url))
        }

        val result = GitHubUpdateRepository(transport, policy) { false }.check(request(), "1.0.0")

        assertTrue(result.isFailure)
        assertTrue(!transportCalled)
    }

    private fun repository(body: String): GitHubUpdateRepository {
        val transport = UpdateHttpTransport { url, _ ->
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
