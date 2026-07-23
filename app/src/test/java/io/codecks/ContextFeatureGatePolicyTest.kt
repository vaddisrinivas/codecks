package io.codecks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextFeatureGatePolicyTest {
    @Test
    fun smartCollectorsStayInertWhenReleaseFlagsAreOff() {
        val source = File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue(source.contains("featureFlags.focusedEnabled(FeatureFlag.SmartSuggestions) && featureFlags.focusedEnabled(FeatureFlag.SmartDeck)"))
        assertTrue(source.contains("val phoneNotificationFlow = remember(notificationFeaturesEnabled)"))
        assertTrue(source.contains("if (notificationFeaturesEnabled) {\n            PhoneNotificationBackplane.notifications"))
        assertTrue(source.contains("flowOf(emptyList<NotificationPreview>())"))
        assertTrue(source.contains("val notificationAccessReady = notificationFeaturesEnabled && PhoneNotificationBackplane.isEnabled(appContext)"))
        assertTrue(source.contains("val smartDeckViewModel: SmartDeckViewModel = viewModel()"))
        assertTrue(source.contains("smartDeckViewModel.updateInputs("))
        assertTrue(source.contains("smartDeckViewModel.effects.collect"))
        assertTrue(source.contains("smartDeckViewModel::run"))
        assertFalse(source.contains("DeterministicSmartEngine"))
        assertFalse(source.contains("FeatureFlag.ContextDeck"))
        assertFalse(source.contains("SmartContextRepository("))
        assertFalse(source.contains("SmartLearningStore("))
        assertFalse(source.contains("smartCollectorsInHome"))
        assertFalse(source.contains("DraftKind.ContextApps"))
        assertFalse(source.contains("PhoneNotificationBackplane.events.collectAsStateWithLifecycle"))
    }
}
