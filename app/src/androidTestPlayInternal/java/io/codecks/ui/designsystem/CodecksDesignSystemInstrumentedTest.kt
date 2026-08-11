package io.codecks.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.codecks.CelebrationOverlay
import io.codecks.HidState
import io.codecks.R
import io.codecks.core.design.CodecksDesignTokens
import io.codecks.core.trackpad.LockscreenControlState
import io.codecks.core.trackpad.LockscreenDecision
import io.codecks.core.trackpad.TrackpadEntryOrigin
import io.codecks.core.trackpad.TrackpadSettings
import io.codecks.ui.mouse.lockscreen.LockscreenTrackpadScreen
import io.codecks.ui.mouse.lockscreen.LockscreenTrackpadUiState
import io.codecks.ui.mouse.TrackpadHostScreen
import io.codecks.ui.keyboard.KeyboardScreen
import io.codecks.ui.theme.CodecksTheme
import io.codecks.ui.theme.CodecksThemeMode
import io.codecks.ui.theme.CodecksThemeSettings
import io.codecks.ui.theme.ThemeBundle
import io.codecks.ui.theme.ThemeArgb
import io.codecks.ui.theme.ThemeColorRole
import io.codecks.ui.theme.ThemePresetCatalog
import io.codecks.ui.theme.ThemePresetId
import io.codecks.ui.theme.ThemeContrast
import io.codecks.ui.settings.CodecksHelperPanel
import io.codecks.ui.settings.CodecksHelperUiState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class CodecksDesignSystemInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<M09ADesignTestActivity>()

    @Test
    fun lockscreenCriticalActionsRemainVisibleAndTouchableAtTwoHundredPercentText() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                CodecksTheme {
                    Box(Modifier.size(width = 412.dp, height = 915.dp)) {
                        LockscreenTrackpadScreen(
                            state = lockedState(),
                            onMove = { _, _ -> },
                            onScroll = { _, _ -> },
                            onClick = {},
                            onPress = {},
                            onReleaseButtons = {},
                            onUnlock = {},
                            onClose = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Unlock for full Codecks").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Unlock to connect").assertIsDisplayed()
    }

    @Test
    fun keyboardComposerKeepsNamedFullWidthActionsAtTwoHundredPercentText() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                CodecksTheme {
                    Box(Modifier.size(width = 412.dp, height = 915.dp)) {
                        KeyboardScreen(
                            state = HidState(isConnected = true),
                            text = "Hello",
                            contentPadding = PaddingValues(0.dp),
                            permissionGranted = true,
                            sendStatus = "Ready to send",
                            onRequestPermission = {},
                            onStart = {},
                            onRefreshHosts = {},
                            onConnect = {},
                            onTextChange = {},
                            onTypeText = {},
                            onClearText = {},
                            onCommand = {},
                            showHostHeader = false,
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Text to type on Mac").assertExists()
        listOf("Send + Enter", "Clear", "⌘ Enter").forEach { label ->
            rule.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
        rule.onAllNodesWithText("Enter")[0].assertHeightIsAtLeast(48.dp)
        assertEquals(
            LiveRegionMode.Polite,
            rule.onNodeWithText("Ready to send").fetchSemanticsNode().config[SemanticsProperties.LiveRegion],
        )
    }

    @Test
    fun firstRunTrackpadSetupRemainsScrollableAtTwoHundredPercentText() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                CodecksTheme {
                    Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                        TrackpadHostScreen(
                            contentPadding = PaddingValues(0.dp),
                            hidState = HidState(),
                            bluetoothPermissionGranted = false,
                            onRequestBluetoothPermission = {},
                            onStartHid = {},
                            onRefreshHosts = {},
                            onConnectHost = {},
                            onConnection = {},
                            onFullscreen = {},
                            content = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Allow Bluetooth").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Allow Bluetooth first").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deckAndHelperRenderInRtlDexWindowWithHighContrastOledThemeAndHaptics() {
        val taps = AtomicInteger()
        val haptics = RecordingHaptics()
        val highContrast = requireNotNull(ThemePresetCatalog.resolve(ThemePresetId.HighContrast.stableId))
        rule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalHapticFeedback provides haptics,
            ) {
                CodecksTheme(
                    settings = CodecksThemeSettings(
                        mode = CodecksThemeMode.Oled,
                        themeBundle = ThemeBundle(highContrast),
                    ),
                ) {
                    Column(Modifier.size(width = 1280.dp, height = 720.dp).background(MaterialTheme.colorScheme.background).testTag("dex-root")) {
                        Row {
                            Box(Modifier.size(24.dp).testTag("rtl-first"))
                            Box(Modifier.size(24.dp).testTag("rtl-second"))
                        }
                        Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.background).testTag("oled-swatch"))
                        DeckControlTile(
                            label = "Complete task",
                            icon = Icons.Outlined.CheckCircle,
                            onClick = { taps.incrementAndGet() },
                            modifier = Modifier.size(width = 240.dp, height = 120.dp).testTag("deck-focus-target"),
                        )
                        CodecksHelperPanel(
                            state = CodecksHelperUiState(
                                pairedDisplayName = "Desk Mac",
                                statusLabel = "Connected",
                                statusDetail = "Ready for helper actions.",
                                discoveredCount = 1,
                                canRunActions = true,
                            ),
                            onConnect = {},
                            onOpenSetup = {},
                            onSearch = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Complete task").assertIsDisplayed().performClick()
        rule.onNodeWithTag("deck-focus-target")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        rule.onNodeWithText("Codecks helper").assertIsDisplayed()
        rule.onNodeWithText("Desk Mac").assertIsDisplayed()
        assertTrue(!rule.onNodeWithText("Connected").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        val first = rule.onNodeWithTag("rtl-first").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("rtl-second").fetchSemanticsNode().boundsInRoot
        assertTrue("RTL must place the first logical child on the right", first.left > second.left)
        val oled = rule.onNodeWithTag("oled-swatch").captureToImage().toPixelMap()[1, 1]
        assertEquals(0f, oled.red, 0.001f)
        assertEquals(0f, oled.green, 0.001f)
        assertEquals(0f, oled.blue, 0.001f)
        assertTrue(ThemeContrast.ratio(highContrast[ThemeColorRole.Primary], highContrast[ThemeColorRole.Background]) >= ThemeContrast.NORMAL_TEXT_MIN)
        rule.onNodeWithTag("dex-root").captureToImage().also { image ->
            assert(image.width > image.height)
        }
        assert(taps.get() == 1)
        assert(haptics.events.get() == 1)
    }

    @Test
    fun ckDeckKeyFocusRingAndClickLongClickHapticsAreObservable() {
        val clicks = AtomicInteger()
        val longClicks = AtomicInteger()
        val haptics = RecordingHaptics()
        val expectedFocusColor = AtomicLong()
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                CodecksTheme {
                    val focusColor = codecksSemanticColorTokens().focus
                        .copy(alpha = CodecksDesignTokens.Focus.ringAlpha)
                        .toArgb().toLong() and 0xffffffffL
                    SideEffect { expectedFocusColor.set(focusColor) }
                    CkDeckKey(
                        label = "Direct key",
                        icon = Icons.Outlined.CheckCircle,
                        onClick = { clicks.incrementAndGet() },
                        onLongClick = { longClicks.incrementAndGet() },
                        state = DeckKeyVisualState.Idle,
                        modifier = Modifier.size(width = 180.dp, height = 120.dp).testTag("direct-key"),
                    )
                }
            }
        }

        val key = rule.onNodeWithTag("direct-key")
        val beforeNode = key.fetchSemanticsNode()
        assertEquals(0f, beforeNode.config[CodecksFocusRingWidthSemantics])
        assertEquals(0L, beforeNode.config[CodecksFocusRingColorSemantics])
        val before = key.captureToImage().toPixelMap()
        key.performSemanticsAction(SemanticsActions.RequestFocus).assertIsFocused()
        rule.waitForIdle()
        val focusedNode = key.fetchSemanticsNode()
        assertEquals(
            CodecksDesignTokens.Focus.ringWidth.value,
            focusedNode.config[CodecksFocusRingWidthSemantics],
        )
        assertEquals(expectedFocusColor.get(), focusedNode.config[CodecksFocusRingColorSemantics])
        val focusedPixels = key.captureToImage().toPixelMap()
        val borderChanged = (0 until focusedPixels.width).any { x ->
            (0..4).any { y -> before[x, y] != focusedPixels[x, y] }
        }
        assertTrue("Focus must visibly change the rendered top border", borderChanged)
        key.performClick()
        key.performTouchInput { longClick() }
        assertEquals(1, clicks.get())
        assertEquals(1, longClicks.get())
        assertTrue(haptics.types.contains(HapticFeedbackType.TextHandleMove))
        assertTrue(haptics.types.contains(HapticFeedbackType.LongPress))
        assertTrue(haptics.events.get() >= 2)
        val pixels = key.captureToImage().toPixelMap()
        assertTrue(pixels[1, 1] != pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun customThemeAndReducedMotionOverlayRenderWithoutDecorativeSemantics() {
        val custom = ThemePresetCatalog.default.withColor(
            ThemeColorRole.Primary,
            requireNotNull(ThemeArgb.of(0xFF52E0C4)),
        )
        rule.setContent {
            CodecksTheme(
                settings = CodecksThemeSettings(
                    mode = CodecksThemeMode.Dark,
                    themeBundle = ThemeBundle(custom),
                ),
            ) {
                CompositionLocalProvider(LocalCodecksMotionPolicy provides CodecksMotionPolicy(reducedMotion = true)) {
                    Box(Modifier.size(width = 412.dp, height = 915.dp).testTag("overlay-root")) {
                        Box(
                            Modifier.size(24.dp)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("custom-primary"),
                        )
                        CelebrationOverlay(label = "Built successfully", onDone = {})
                    }
                }
            }
        }

        rule.onNodeWithText("Built successfully").assertIsDisplayed()
        assertEquals(
            LiveRegionMode.Polite,
            rule.onNodeWithText("Built successfully").fetchSemanticsNode().config[SemanticsProperties.LiveRegion],
        )
        val resolvedPrimary = rule.onNodeWithTag("custom-primary").captureToImage().toPixelMap()[8, 8]
        assertEquals(custom[ThemeColorRole.Primary].value, resolvedPrimary.toArgb().toLong() and 0xffffffffL)
        rule.onNodeWithTag("overlay-root").captureToImage().also { image ->
            assert(image.width > 0 && image.height > 0)
        }
        rule.onNodeWithText("🎉").assertDoesNotExist()
    }

    @Test
    fun widgetInitialLayoutInflatesWithTokenOwnedFallbacks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = RemoteViews(context.packageName, R.layout.trackpad_widget).apply(context, null)
        val root = requireNotNull(view.findViewById<android.view.View>(R.id.trackpad_widget_root))
        val icon = requireNotNull(view.findViewById<android.view.View>(R.id.trackpad_widget_icon))
        assertEquals(context.getString(R.string.widget_trackpad_action), root.contentDescription)
        assertTrue(root.isFocusable)
        assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO, icon.importantForAccessibility)
        assert(context.resources.getColor(R.color.codecks_widget_canvas_fallback, context.theme) != 0)
    }

    @Test
    fun animatorScaleChangesUpdateMotionPolicyWithoutRecreatingComposition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val original = android.provider.Settings.Global.getFloat(
            resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        try {
            rule.setContent {
                CodecksTheme {
                    Text(if (LocalCodecksMotionPolicy.current.reducedMotion) "Reduced" else "Motion")
                }
            }
            instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale 0").close()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodes(hasText("Reduced")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Reduced").assertIsDisplayed()
        } finally {
            instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $original").close()
        }
    }

    private class RecordingHaptics : HapticFeedback {
        val events = AtomicInteger()
        val types = java.util.concurrent.CopyOnWriteArrayList<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            types += hapticFeedbackType
            events.incrementAndGet()
        }
    }

    private fun lockedState() = LockscreenTrackpadUiState(
        controlState = LockscreenControlState(
            keyguardShowing = true,
            deviceLocked = true,
            userUnlockedSinceBoot = false,
            hidConnected = false,
            selectedHostPresent = false,
            bluetoothPermissionGranted = false,
            featureEnabled = true,
            entryOrigin = TrackpadEntryOrigin.InternalWidget,
        ),
        decision = LockscreenDecision.RequireUnlock,
        settings = TrackpadSettings(),
    )
}
