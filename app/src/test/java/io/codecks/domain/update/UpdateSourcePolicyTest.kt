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
        ).forEach { url ->
            assertTrue("$url should fail closed", runCatching { policy.requireApiUrl(url) }.isFailure)
        }
    }
}
