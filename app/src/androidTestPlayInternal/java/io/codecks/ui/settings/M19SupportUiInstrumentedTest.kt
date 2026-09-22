package io.codecks.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import io.codecks.ui.theme.CodecksTheme
import io.codecks.ui.designsystem.M09ADesignTestActivity
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class M19SupportUiInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<M09ADesignTestActivity>()

    @Test
    fun previewShowsBoundedRedactedHealthAndGeneratesWithoutOpeningChooser() {
        val generated = AtomicInteger()
        rule.setContent {
            CodecksTheme {
                SupportBundleDialog(
                    state = SupportBundleUiState.Preview(
                        summary = SupportBundlePreviewSummary(
                            build = "42 · release",
                            connection = "Degraded",
                            hid = "Ready",
                            bluetoothPermission = "Granted",
                            notificationPermission = "Denied",
                            batteryPolicy = "Saver active",
                        ),
                    ),
                    onGenerate = { generated.incrementAndGet() },
                    onCancel = {},
                )
            }
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Preview ready")).assertIsDisplayed()
        rule.onNodeWithText("Connection: Degraded · Bluetooth input: Ready").assertIsDisplayed()
        rule.onNodeWithText("Battery: Saver active").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Never includes credentials, hosts, usernames, fingerprints, clipboard text, commands, raw logs, prompts, responses, tokens, account or purchase identifiers, paths, or device serials.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Codecks opens Android’s share picker. It never uploads this bundle.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Generate support bundle").performClick()
        assertEquals(1, generated.get())
        rule.onNodeWithText("Share picker opened").assertDoesNotExist()
    }

    @Test
    fun failedShareExposesBoundedRetryWithoutLaunchingExternalChooser() {
        val retried = AtomicInteger()
        rule.setContent {
            CodecksTheme {
                SupportBundleDialog(
                    state = SupportBundleUiState.Failure(SupportBundleFailure.SHARE_PICKER_UNAVAILABLE),
                    onGenerate = {},
                    onCancel = {},
                    onRetryShare = { retried.incrementAndGet() },
                )
            }
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Support bundle failed")).assertIsDisplayed()
        rule.onNodeWithContentDescription("Open share picker again").performClick()
        assertEquals(1, retried.get())
        rule.onNodeWithText("Share picker opened").assertDoesNotExist()
    }

    @Test
    fun deleteFailureExposesRepairActionWithoutSecretSurface() {
        val repaired = AtomicInteger()
        rule.setContent {
            CodecksTheme {
                SupportBundleDialog(
                    state = SupportBundleUiState.Failure(SupportBundleFailure.DELETE_FAILED),
                    onGenerate = {},
                    onCancel = {},
                    onDeletePending = { repaired.incrementAndGet() },
                )
            }
        }
        rule.onNodeWithContentDescription("Retry deleting support bundle").performClick()
        assertEquals(1, repaired.get())
        rule.onNodeWithText("password", substring = true, ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("token", substring = true, ignoreCase = true).assertDoesNotExist()
    }
}
