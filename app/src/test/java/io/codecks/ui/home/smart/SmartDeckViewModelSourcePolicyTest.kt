package io.codecks.ui.home.smart

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class SmartDeckViewModelSourcePolicyTest {

    private fun readSource(path: String): String = sequenceOf(
        File(path),
        File("app/$path"),
    )
        .first { it.exists() }
        .readText()

    @Test
    fun mainActivityDoesNotContainSmartCoreConstructionOrState() {
        val source = readSource("src/main/java/io/codecks/MainActivity.kt")
        assertFalse(source.contains("SmartLearningStore("))
        assertFalse(source.contains("DeterministicSmartEngine("))
        assertFalse(source.contains("pendingSmartRuns"))
        assertFalse(source.contains("temporaryHiddenCandidateIds"))
    }

    @Test
    fun smartDeckViewModelFileExists() {
        val source = readSource("src/main/java/io/codecks/ui/home/smart/SmartDeckViewModel.kt")
        assertNotNull(source)
        assertFalse(source.isBlank())
        assertFalse(source.contains("ActionRunner"))
        assertFalse(source.contains("LocalActionDispatcher"))
        assertFalse(source.contains("homeViewModel.run"))
    }
}
