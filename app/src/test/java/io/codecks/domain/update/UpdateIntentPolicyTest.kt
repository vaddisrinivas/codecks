package io.codecks.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateIntentPolicyTest {
    private val policy = UpdateIntentPolicy(
        UpdateSourcePolicy(
            releasesApiUrl = "https://api.github.com/repos/example/codecks/releases",
            apiHosts = setOf("api.github.com"),
            releasePageHosts = setOf("github.com"),
        ),
    )

    @Test
    fun allowsOnlyConfiguredHttpsReleaseHost() {
        val allowed = "https://github.com/example/codecks/releases/tag/v1.2.3"
        assertEquals(allowed, policy.allowedExternalUrl(allowed))
        assertNull(policy.allowedExternalUrl("http://github.com/example/codecks/releases/tag/v1.2.3"))
        assertNull(policy.allowedExternalUrl("https://github.com.evil.example/release"))
        assertNull(policy.allowedExternalUrl("https://user@github.com/release"))
    }
}
