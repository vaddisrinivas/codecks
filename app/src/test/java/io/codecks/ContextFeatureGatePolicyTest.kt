package io.codecks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextFeatureGatePolicyTest {
    @Test
    fun contextCollectorsStayInertWhenReleaseFlagsAreOff() {
        val source = File("src/main/java/io/codecks/MainActivity.kt").readText()

        assertTrue(source.contains("featureFlags.focusedEnabled(FeatureFlag.Labs) && featureFlags.focusedEnabled(FeatureFlag.ContextDeck)"))
        assertTrue(source.contains("val phoneNotificationFlow = remember(contextFeaturesEnabled)"))
        assertTrue(source.contains("if (contextFeaturesEnabled) {\n            PhoneNotificationBackplane.notifications"))
        assertTrue(source.contains("flowOf(emptyList<NotificationPreview>())"))
        assertTrue(source.contains("val notificationAccessReady = contextFeaturesEnabled && PhoneNotificationBackplane.isEnabled(appContext)"))
        assertTrue(source.contains("if (!contextFeaturesEnabled) return@LaunchedEffect"))
        assertTrue(source.contains("if (contextFeaturesEnabled) {\n            ContextAppRanker.rank"))
        assertTrue(source.contains("if (contextFeaturesEnabled) {\n            ContextDeckTileRanker.rank"))
        assertFalse(source.contains("PhoneNotificationBackplane.events.collectAsStateWithLifecycle"))
    }
}
