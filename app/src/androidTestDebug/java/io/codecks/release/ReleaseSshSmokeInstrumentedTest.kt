package io.codecks.release

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.MainActivity
import java.io.FileInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.text.lowercase

class ReleaseSshSmokeInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun requirePhysicalSetup() {
        val shouldRun = InstrumentationRegistry.getArguments().getString("requirePhysicalMac", "false")
        assumeTrue("Physical SSH smoke requires explicit release runner argument.", shouldRun == "true")
    }

    private fun waitForTag(tag: String) {
        rule.waitUntil(timeoutMillis = 30_000) {
            try {
                rule.onNodeWithTag(tag).fetchSemanticsNode()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun actionResultText(actionId: String): String {
        return readNodeText(rule.onNodeWithTag("deck-action-result-$actionId"))
    }

    private fun readNodeText(node: SemanticsNodeInteraction): String {
        val rawText = node.fetchSemanticsNode().config[SemanticsProperties.Text]

        return when (rawText) {
            is String -> rawText.lowercase()
            is Iterable<*> -> rawText.joinToString(" ", transform = {
                when (it) {
                    is AnnotatedString -> it.text
                    null -> ""
                    else -> it.toString()
                }
            }).lowercase()
            else -> rawText?.toString()?.lowercase().orEmpty()
        }
    }

    private fun isKnownBundledAction(actionId: String): Boolean {
        return when (actionId.lowercase()) {
            "terminal", "desktop", "documents", "downloads", "settings", "clipboard" -> true
            else -> false
        }
    }

    private fun commandOutput(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return try {
            FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        } finally {
            pfd.close()
        }
    }

    private fun actionCoordsFor(actionId: String): Pair<Int, Int>? {
        val actionLabel = when (actionId.lowercase()) {
            "terminal" -> "Terminal"
            "desktop" -> "Desktop"
            "documents" -> "Documents"
            "downloads" -> "Downloads"
            "settings" -> "Settings"
            "clipboard" -> "Clipboard"
            else -> return null
        }

        commandOutput("uiautomator dump /sdcard/window.xml")
        val xml = commandOutput("cat /sdcard/window.xml")
        val nodeMatcher = Regex("""<node[^>]*text="${actionLabel}"[^>]*>""")
        val enabledMatcher = Regex("""enabled="(true|false)""" )
        val boundsMatcher = Regex("""bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]""" )

        for (match in nodeMatcher.findAll(xml)) {
            val node = match.value
            if ((enabledMatcher.find(node)?.groupValues?.get(1)) != "true") continue
            val bounds = boundsMatcher.find(node) ?: continue
            val x1 = bounds.groupValues[1].toIntOrNull() ?: continue
            val y1 = bounds.groupValues[2].toIntOrNull() ?: continue
            val x2 = bounds.groupValues[3].toIntOrNull() ?: continue
            val y2 = bounds.groupValues[4].toIntOrNull() ?: continue
            return Pair((x1 + x2) / 2, (y1 + y2) / 2)
        }
        return null
    }

    private fun tapAt(x: Int, y: Int) {
        val command = "input tap $x $y"
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @Test
    fun signedReleaseRunsBundledFinderAction() {
        requirePhysicalSetup()

        waitForTag("home-connection-status")
        rule.onNodeWithTag("home-connection-status").fetchSemanticsNode()
        rule.onNodeWithTag("home-connection-status").assertTextContains("Ready", substring = true)

        val preferredActionIds = listOf(
            "terminal",
            "desktop",
            "documents",
            "downloads",
            "settings",
            "clipboard",
        )

        val attemptedActionIds = mutableListOf<String>()
        var selectedActionId: String? = null
        var selectedResultText: String? = null

        for (id in preferredActionIds) {
            println("Release smoke: testing action=$id")
            if (!isKnownBundledAction(id)) {
                attemptedActionIds.add(id)
                println("Release smoke: action=$id unsupported")
                continue
            }
            val actionCoords = actionCoordsFor(id)
            if (actionCoords == null) {
                attemptedActionIds.add(id)
                println("Release smoke: action=$id is disabled")
                continue
            }

            attemptedActionIds.add(id)
            val (x, y) = actionCoords

            try {
                tapAt(x, y)
                println("Release smoke: tapped action=$id")
            } catch (_: AssertionError) {
                println("Release smoke: assertion when tapping action=$id")
                continue
            } catch (_: IllegalStateException) {
                println("Release smoke: illegal state when tapping action=$id")
                continue
            } catch (_: Exception) {
                println("Release smoke: exception when tapping action=$id")
                continue
            }

            try {
        waitForTag("deck-action-result-$id")
            } catch (_: AssertionError) {
                println("Release smoke: no result for action=$id")
                continue
            }

            val resultText = actionResultText(id)
            val isFailure = resultText.contains("failed")
                || resultText.contains("error")
                || resultText.contains("not configured")
                || resultText.contains("not ready")
                || resultText.contains("connect")
            if (isFailure) {
                println("Release smoke: action=$id returned failure")
                continue
            }

            selectedActionId = id
            selectedResultText = resultText
            break
        }

        if (selectedActionId == null) {
            fail("No bundled SSH action produced a successful result. Attempted: ${attemptedActionIds.joinToString(", ")}")
        }
        val actionId: String = selectedActionId!!

        try {
            waitForTag("deck-action-result")
        } catch (_: AssertionError) {
            fail("Result summary for action=$actionId was not emitted")
        }

        val resultText = selectedResultText
            ?: actionResultText(actionId)

        assertTrue("Runner action result should indicate success", resultText.contains("completed"))
        assertFalse("Runner action result should not report a setup failure", resultText.contains("connect"))
    }
}
