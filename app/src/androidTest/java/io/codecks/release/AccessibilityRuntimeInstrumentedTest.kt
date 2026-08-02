package io.codecks.release

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.codecks.navigation.HomeRoute
import io.codecks.ui.app.AccessibleStatus
import io.codecks.ui.app.AccessibleStatusKind
import io.codecks.ui.app.CodecksAppShell
import org.junit.Rule
import org.junit.Test

class AccessibilityRuntimeInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun committedStatusKeepsPoliteLiveRegionAcrossStateChange() {
        val status = mutableStateOf("Connecting")
        compose.setContent {
            MaterialTheme {
                AccessibleStatus(
                    stateDescription = status.value,
                    kind = AccessibleStatusKind.Information,
                    announceChanges = true,
                    announcementKey = status.value,
                )
            }
        }
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

        compose.onNode(hasStateDescription("Connecting")).assert(polite)
        compose.runOnIdle { status.value = "Connected" }
        compose.onNode(hasStateDescription("Connected")).assert(polite)
    }

    @Test
    fun largeComposeViewportAtTwoHundredPercentFontUsesRuntimeReflowPolicy() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                Box(Modifier.requiredSize(1280.dp, 720.dp)) {
                    CodecksAppShell(
                        snackbarHostState = SnackbarHostState(),
                        currentRoute = HomeRoute,
                        backStackSize = 1,
                        fullscreen = false,
                        onBack = {},
                        onDestinationSelected = {},
                        onOpenSettings = {},
                        onRequestFullscreen = {},
                        onExitFullscreen = {},
                    ) { }
                }
            }
        }

        compose.onNodeWithText("More").fetchSemanticsNode()
        compose.onNodeWithText("Deck").fetchSemanticsNode()
    }
}
