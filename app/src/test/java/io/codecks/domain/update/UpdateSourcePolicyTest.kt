package io.codecks.domain.update

import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSourcePolicyTest {
    private val policy = UpdateSourcePolicy(
        releasesApiUrl = "https://api.github.com/repos/example/codecks/releases",
        apiHosts = setOf("api.github.com"),
        releasePageHosts = setOf("github.com"),
    )

    @Test
    fun rejectsHttpUserInfoBlockedPortsFragmentsAndHostSuffixes() {
        listOf(
            "http://api.github.com/repos/example/codecks/releases",
            "https://user@api.github.com/repos/example/codecks/releases",
            "https://api.github.com:444/repos/example/codecks/releases",
            "https://api.github.com/repos/example/codecks/releases#fragment",
            "https://api.github.com.evil.example/repos/example/codecks/releases",
            "https://api.github.com/repos/another/codecks/releases",
            "https://api.github.com/repos/example/codecks/releases?per_page=100",
        ).forEach { url ->
            assertTrue("$url should fail closed", runCatching { policy.requireApiUrl(url) }.isFailure)
        }
    }

    @Test
    fun releasePageMustBeExactConfiguredRepositoryTagPath() {
        listOf(
            "https://github.com/another/codecks/releases/tag/v1.0.0",
            "https://github.com/example/codecks/issues/1",
            "https://github.com/example/codecks/releases/tag/",
            "https://github.com/example/codecks/releases/tag/v1.0.0?download=1",
            "https://github.com/example/codecks/releases/tag/v1.0.0/notes",
            "https://github.com/example/codecks/releases/tag/v1.0.0/../../issues",
            "https://github.com/example/codecks/releases/tag/v1%2Fnotes",
            "https://github.com/example/codecks/releases/tag/%2e%2e",
        ).forEach { url ->
            assertTrue("$url should fail closed", runCatching { policy.requireReleasePageUrl(url) }.isFailure)
        }
    }
}
