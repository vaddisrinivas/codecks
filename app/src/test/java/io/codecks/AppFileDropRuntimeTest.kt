package io.codecks

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFileDropRuntimeTest {
    @Test
    fun fileNamesAreBoundedAndCannotTraverse() {
        assertEquals("1-notes.txt", safeDropFileName(" notes.txt ", 0))
        assertEquals("file-2", safeDropFileName("../secret", 1))
        assertEquals("file-3", safeDropFileName("bad/name", 2))
        assertEquals("file-4", safeDropFileName("\u0000oops", 3))
        assertEquals(90, safeDropFileName("a".repeat(200), 4).length)
    }
}
