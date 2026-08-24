package io.codecks.data.contextdeck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionWindowSpaceRepositoryTest {
    @Test
    fun parsesClosedBoundedMap() {
        val map = parseWindowSpaceMap(
            """{"windows":[{"id":"com.apple.finder:0","title":"Downloads","appName":"Finder","bundleId":"com.apple.finder","spaceId":"current","displayId":"","focused":true}],"spaces":[{"id":"current","label":"Current Space","displayId":"","focused":true}]}""",
            10L,
        )
        assertEquals("Downloads", map.windows.single().title)
        assertEquals("Current Space", map.spaces.single().label)
    }

    @Test
    fun rejectsUnknownFieldsDuplicateFocusAndOversize() {
        assertTrue(
            runCatching { parseWindowSpaceMap("""{"windows":[],"spaces":[],"extra":true}""", 0) }.isFailure,
        )
        val duplicateFocus = """{"windows":[{"id":"a:0","title":"A","appName":"A","bundleId":"a.a","spaceId":"","displayId":"","focused":true},{"id":"b:0","title":"B","appName":"B","bundleId":"b.b","spaceId":"","displayId":"","focused":true}],"spaces":[]}"""
        assertTrue(runCatching { parseWindowSpaceMap(duplicateFocus, 0) }.isFailure)
        assertTrue(runCatching { parseWindowSpaceMap("x".repeat(65 * 1024), 0) }.isFailure)
    }

    @Test
    fun focusCommandAcceptsOnlyOpaqueParsedBundleAndIndex() {
        val command = focusWindowCommand("com.apple.finder", 2)
        assertTrue(command.contains("bundleIdentifier:\"com.apple.finder\""))
        assertTrue(command.contains("ws[2]"))
        assertTrue(runCatching { focusWindowCommand("bad\";evil", 0) }.isFailure)
        assertTrue(runCatching { focusWindowCommand("com.apple.finder", 99) }.isFailure)
    }
}
