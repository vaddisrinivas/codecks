package io.codex.s23deck.ui.app

import io.codex.s23deck.domain.commerce.DEFAULT_FEATURE_FLAGS
import io.codex.s23deck.domain.commerce.FeatureFlag
import io.codex.s23deck.navigation.ActivityRoute
import io.codex.s23deck.navigation.AdvancedRoute
import io.codex.s23deck.navigation.AiBuilderRoute
import io.codex.s23deck.navigation.AiProviderRoute
import io.codex.s23deck.navigation.AppearanceRoute
import io.codex.s23deck.navigation.AutomationsRoute
import io.codex.s23deck.navigation.BluetoothRoute
import io.codex.s23deck.navigation.ClipboardRoute
import io.codex.s23deck.navigation.ConnectionRoute
import io.codex.s23deck.navigation.ContextDeckRoute
import io.codex.s23deck.navigation.DevicesRoute
import io.codex.s23deck.navigation.EditorRoute
import io.codex.s23deck.navigation.HomeRoute
import io.codex.s23deck.navigation.KeyboardRoute
import io.codex.s23deck.navigation.MouseRoute
import io.codex.s23deck.navigation.PremiumRoute
import io.codex.s23deck.navigation.SettingsRoute
import io.codex.s23deck.navigation.WidgetRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun defaultMainDestinations_focusOnCoreCodecksPillars() {
        val labels = mainDestinations(DEFAULT_FEATURE_FLAGS).map { it.label }

        assertEquals(listOf("Deck", "Trackpad", "Automations", "Settings"), labels)
        assertFalse(labels.contains("Context"))
        assertFalse(labels.contains("AI"))
        assertFalse(labels.contains("Keyboard"))
        assertFalse(labels.contains("Clipboard"))
    }

    @Test
    fun hiddenDestinationRequests_fallBackToSettingsUnlessFlagged() {
        assertEquals(MouseRoute, destinationRequestToRoute("keyboard", DEFAULT_FEATURE_FLAGS))
        assertEquals(BluetoothRoute, destinationRequestToRoute("bluetooth", DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, destinationRequestToRoute("clipboard", DEFAULT_FEATURE_FLAGS))
        assertEquals(AutomationsRoute, destinationRequestToRoute("automations", DEFAULT_FEATURE_FLAGS))
        assertEquals(AiBuilderRoute, destinationRequestToRoute("ai", DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, destinationRequestToRoute("context", DEFAULT_FEATURE_FLAGS))

        val disabled = DEFAULT_FEATURE_FLAGS + mapOf(
            FeatureFlag.Trackpad to false,
            FeatureFlag.Automations to false,
            FeatureFlag.Ai to false,
            FeatureFlag.AiBuilder to false,
            FeatureFlag.ContextDeck to false,
        )
        assertEquals(SettingsRoute, destinationRequestToRoute("keyboard", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("automations", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("ai", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("context", disabled))
        assertEquals(SettingsRoute, destinationRequestToRoute("definitely-not-real", disabled))
    }

    @Test
    fun aiBuilderFlagDoesNotMakeAiTopLevel() {
        val flags = DEFAULT_FEATURE_FLAGS + (FeatureFlag.AiBuilder to false)

        val labels = mainDestinations(flags).map { it.label }

        assertFalse(labels.contains("AI"))
        assertEquals(SettingsRoute, guardRoute(AiBuilderRoute, flags))
    }

    @Test
    fun guardRoute_blocksHiddenOrUnsupportedRoutesByDefault() {
        assertEquals(HomeRoute, guardRoute(HomeRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(MouseRoute, guardRoute(MouseRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(AutomationsRoute, guardRoute(AutomationsRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(AiBuilderRoute, guardRoute(AiBuilderRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(AiProviderRoute, guardRoute(AiProviderRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(ConnectionRoute, guardRoute(ConnectionRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(BluetoothRoute, guardRoute(BluetoothRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(ContextDeckRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(WidgetRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(AppearanceRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(ActivityRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(DevicesRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(PremiumRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(AdvancedRoute, DEFAULT_FEATURE_FLAGS))
        assertEquals(SettingsRoute, guardRoute(EditorRoute, DEFAULT_FEATURE_FLAGS + (FeatureFlag.DeckEditor to false)))
        assertEquals(SettingsRoute, guardRoute(KeyboardRoute, DEFAULT_FEATURE_FLAGS + (FeatureFlag.Keyboard to true)))
        assertEquals(SettingsRoute, guardRoute(ClipboardRoute, DEFAULT_FEATURE_FLAGS + (FeatureFlag.Clipboard to true)))
    }

    @Test
    fun defaultFlags_keepNonCoreLaunchPagesOff() {
        listOf(
            FeatureFlag.ContextDeck,
            FeatureFlag.Widget,
            FeatureFlag.Clipboard,
            FeatureFlag.Premium,
            FeatureFlag.Paywall,
            FeatureFlag.Advanced,
            FeatureFlag.Activity,
            FeatureFlag.Devices,
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
        ).forEach { flag ->
            assertTrue("$flag should stay enabled for local-only v1", DEFAULT_FEATURE_FLAGS[flag] == true)
        }
    }

    @Test
    fun guardRoute_allowsOptionalRoutesOnlyWhenTheirFlagsAreOn() {
        val enabled = DEFAULT_FEATURE_FLAGS + mapOf(
            FeatureFlag.Activity to true,
            FeatureFlag.Devices to true,
            FeatureFlag.Premium to true,
            FeatureFlag.Paywall to true,
            FeatureFlag.Advanced to true,
            FeatureFlag.ContextDeck to true,
        )

        assertTrue(routeEnabled(ActivityRoute, enabled))
        assertTrue(routeEnabled(DevicesRoute, enabled))
        assertTrue(routeEnabled(PremiumRoute, enabled))
        assertTrue(routeEnabled(AdvancedRoute, enabled))
        assertTrue(routeEnabled(ContextDeckRoute, enabled))
        assertEquals(ActivityRoute, guardRoute(ActivityRoute, enabled))
        assertEquals(DevicesRoute, guardRoute(DevicesRoute, enabled))
        assertEquals(PremiumRoute, guardRoute(PremiumRoute, enabled))
        assertEquals(AdvancedRoute, guardRoute(AdvancedRoute, enabled))
        assertEquals(ContextDeckRoute, guardRoute(ContextDeckRoute, enabled))
    }
}
