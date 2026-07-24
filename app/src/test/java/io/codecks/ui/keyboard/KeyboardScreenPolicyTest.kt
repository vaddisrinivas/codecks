package io.codecks.ui.keyboard

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardScreenPolicyTest {
    @Test
    fun composerKeepsEnterAndCommandEnterNextToSendControls() {
        val source = File("src/main/java/io/codecks/ui/keyboard/KeyboardScreen.kt").readText()

        assertTrue(source.contains("""else "Send + Enter""""))
        assertTrue(source.contains("""label = "Enter""""))
        assertTrue(source.contains("""label = "⌘ Enter""""))
        assertTrue(source.contains("HidCommand.CommandEnter"))
    }
}
