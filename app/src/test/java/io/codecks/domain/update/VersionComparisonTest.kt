package io.codecks.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    @Test
    fun comparesCurrentOlderNewerAndSuffixVersions() {
        val current = version("1.2.3")

        assertEquals(0, current.compareTo(version("v1.2.3")))
        assertTrue(version("1.2.4") > current)
        assertTrue(version("1.2.2") < current)
        assertTrue(version("1.2.3-rc.1") < current)
        assertTrue(version("1.2.3-rc.2") > version("1.2.3-rc.1"))
        assertEquals(version("1.2.3"), version("1.2.3+build.9"))
    }

    @Test
    fun malformedVersionsFailDeterministically() {
        listOf("", "1", "1.2", "01.2.3", "1.2.3-01", "latest", "1.2.3.4").forEach {
            assertTrue("$it should fail", SemanticVersion.parse(it).isFailure)
        }
    }

    private fun version(value: String): SemanticVersion = SemanticVersion.parse(value).getOrThrow()
}
