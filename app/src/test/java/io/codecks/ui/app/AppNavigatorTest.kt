package io.codecks.ui.app

import io.codecks.domain.features.DEFAULT_FEATURE_FLAGS
import io.codecks.domain.features.FeatureFlag
import io.codecks.navigation.AiBuilderRoute
import io.codecks.navigation.AiProviderRoute
import io.codecks.navigation.AutomationsRoute
import io.codecks.navigation.ClipboardRoute
import io.codecks.navigation.EditorRoute
import io.codecks.navigation.HomeRoute
import io.codecks.navigation.KeyboardRoute
import io.codecks.navigation.MouseRoute
import io.codecks.navigation.SettingsRoute
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun defaultMainDestinations_focusOnCoreCodecksPillars() {
        val labels = mainDestinations(DEFAULT_FEATURE_FLAGS).map { it.label }

        assertEquals(listOf("Deck", "Trackpad", "Keyboard", "Clipboard", "Rules", "AI", "Settings"), labels)
        assertFalse(labels.contains("Context"))
    }

    @Test
    fun hiddenDestinationRequests_fallBackToSettingsUnlessFlagged() {
        assertEquals(KeyboardRoute, destinationRequestToRoute("keyboard", DEFAULT_FEATURE_FLAGS))
        assertEquals(MouseRoute, destinationRequestToRoute("bluetooth", DEFAULT_FEATURE_FLAGS))
        assertEquals(ClipboardRoute, destinationRequestToRoute("clipboard", DEFAULT_FEATURE_FLAGS))
        assertEquals(AutomationsRoute, destinationRequestToRoute("automations", DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, destinationRequestToRoute("connection", DEFAULT_FEATURE_FLAGS))
        assertEquals(AiBuilderRoute, destinationRequestToRoute("ai", DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, destinationRequestToRoute("context", DEFAULT_FEATURE_FLAGS))

        val disabled = DEFAULT_FEATURE_FLAGS + mapOf(
            FeatureFlag.Trackpad to false,
            FeatureFlag.Automations to false,
            FeatureFlag.Ai to false,
            FeatureFlag.AiBuilder to false,
            FeatureFlag.ContextDeck to false,
            FeatureFlag.Keyboard to false,
            FeatureFlag.Clipboard to false,
        )
        assertEquals(SettingsRoute, destinationRequestToRoute("keyboard", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("clipboard", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("automations", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("ai", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("context", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("definitely-not-real", disabled))
    }

    @Test
    fun aiFeatureMakesAiTopLevelEvenWhenBuilderIsDisabled() {
        val flags = DEFAULT_FEATURE_FLAGS + (FeatureFlag.AiBuilder to false)

        val labels = mainDestinations(flags).map { it.label }

        assertTrue(labels.contains("AI"))
        assertEquals(AiBuilderRoute, guardRoute(AiBuilderRoute, flags))
    }

    @Test
    fun guardRoute_blocksHiddenOrUnsupportedRoutesByDefault() {
        assertEquals(HomeRoute, guardRoute(HomeRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(MouseRoute, guardRoute(MouseRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(KeyboardRoute, guardRoute(KeyboardRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(ClipboardRoute, guardRoute(ClipboardRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(AutomationsRoute, guardRoute(AutomationsRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(AiBuilderRoute, guardRoute(AiBuilderRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(AiProviderRoute, guardRoute(AiProviderRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(EditorRoute, DEFAULT_FEATURE_FLAGS + (FeatureFlag.DeckEditor to false)))
        assertEquals(SettingsRoute, guardRoute(DeletedRoute, DEFAULT_FEATURE_FLAGS))
    }

    @Test
    fun defaultFlags_keepNonCoreLaunchPagesOff() {
        listOf(
            FeatureFlag.ContextDeck,
            FeatureFlag.Labs,
        ).forEach { flag ->
            assertFalse("$flag should stay behind a flag", DEFAULT_FEATURE_FLAGS[flag] == true)
        }

        listOf(
            FeatureFlag.Deck,
            FeatureFlag.Trackpad,
            FeatureFlag.Automations,
            FeatureFlag.DeckEditor,
            FeatureFlag.Connection,
            FeatureFlag.Settings,
            FeatureFlag.Ai,
            FeatureFlag.AiBuilder,
            FeatureFlag.Keyboard,
            FeatureFlag.Clipboard,
        ).forEach { flag ->
            assertTrue("$flag should stay enabled for local-only v1", DEFAULT_FEATURE_FLAGS[flag] == true)
        }
    }

    @Test
    fun guardRoute_keepsDeletedSurfacesBlockedEvenWhenTheirFlagsAreOn() {
        val enabled = DEFAULT_FEATURE_FLAGS + mapOf(
            FeatureFlag.ContextDeck to true,
        )

        assertFalse(routeEnabled(DeletedRoute, enabled))
        assertEquals(SettingsRoute, guardRoute(DeletedRoute, enabled))
    }
}

@Serializable
private data object DeletedRoute : NavKey
