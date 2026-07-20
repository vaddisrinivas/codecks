package io.codex.s23deck.core.trackpad

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trackpadDataStore by preferencesDataStore(name = "trackpad_settings")

data class TrackpadSettings(
    val pointerSpeed: Float = 0.72f,
    val acceleration: Float = 1.0f,
    val scrollSpeed: Float = 1.0f,
    val naturalScroll: Boolean = true,
    val scrollRailEnabled: Boolean = true,
    val scrollRailInverted: Boolean = false,
    val railSide: TrackpadRailSide = TrackpadRailSide.Right,
    val rotation: TrackpadRotation = TrackpadRotation.Deg0,
    val hapticsEnabled: Boolean = true,
    val pointerTraceEnabled: Boolean = false,
    val quietModeEnabled: Boolean = true,
    val idleBlankTimeoutMillis: Int = 120_000,
    val backgroundOpacity: Float = 0.48f,
    val clockStyle: TrackpadClockStyle = TrackpadClockStyle.Stacked,
    val floatingMenuLayout: TrackpadFloatingMenuLayout = TrackpadFloatingMenuLayout.Horizontal,
    val doubleTapTimeoutMillis: Int = 620,
    val tapMovementThresholdPx: Float = TrackpadGestureEngine.DEFAULT_TAP_MOVEMENT_THRESHOLD_PX,
    val tapCorrectionCount: Int = 0,
    val sPenPrecisionEnabled: Boolean = true,
    val dragLockEnabled: Boolean = false,
    val labsEnabled: Boolean = false,
    val airMouseEnabled: Boolean = false,
    val airTouchEnabled: Boolean = false,
    val backTapEnabled: Boolean = false,
    val volumeKeysEnabled: Boolean = false,
)

enum class TrackpadRailSide {
    Left,
    Right,
}

enum class TrackpadRotation(val label: String) {
    Deg0("0"),
    Deg90("90"),
    Deg180("180"),
    Deg270("270"),
}

enum class TrackpadClockStyle(val label: String) {
    Stacked("Stacked"),
    Compact("Compact"),
    Focus("Focus"),
}

enum class TrackpadFloatingMenuLayout(val label: String) {
    Vertical("Vertical"),
    Horizontal("Horizontal"),
}

@Singleton
class TrackpadSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val settings: Flow<TrackpadSettings> = context.trackpadDataStore.data.map { preferences ->
        TrackpadSettings(
            pointerSpeed = preferences[POINTER_SPEED]?.coerceIn(0.3f, 1.35f) ?: 0.72f,
            acceleration = preferences[ACCELERATION]?.coerceIn(0.5f, 1.75f) ?: 1.0f,
            scrollSpeed = preferences[SCROLL_SPEED]?.coerceIn(0.35f, 1.8f) ?: 1.0f,
            naturalScroll = preferences[NATURAL_SCROLL] ?: true,
            scrollRailEnabled = preferences[SCROLL_RAIL_ENABLED] ?: true,
            scrollRailInverted = preferences[SCROLL_RAIL_INVERTED] ?: false,
            railSide = preferences[RAIL_SIDE]?.let(::enumValueOrNull) ?: TrackpadRailSide.Right,
            rotation = preferences[ROTATION]?.let(::enumValueOrNull) ?: TrackpadRotation.Deg0,
            hapticsEnabled = preferences[HAPTICS_ENABLED] ?: true,
            pointerTraceEnabled = preferences[POINTER_TRACE_ENABLED] ?: false,
            quietModeEnabled = preferences[QUIET_MODE_ENABLED] ?: true,
            idleBlankTimeoutMillis = preferences[IDLE_BLANK_TIMEOUT_MS]?.coerceIn(30_000, 600_000) ?: 120_000,
            backgroundOpacity = preferences[BACKGROUND_OPACITY]?.coerceIn(0.05f, 0.72f) ?: 0.48f,
            clockStyle = preferences[CLOCK_STYLE]?.let(::enumValueOrNull) ?: TrackpadClockStyle.Stacked,
            floatingMenuLayout = preferences[FLOATING_MENU_LAYOUT]?.let(::enumValueOrNull) ?: TrackpadFloatingMenuLayout.Horizontal,
            doubleTapTimeoutMillis = preferences[DOUBLE_TAP_TIMEOUT_MS]?.takeUnless { it <= 420 }?.coerceIn(350, 900) ?: 620,
            tapMovementThresholdPx = preferences[TAP_MOVEMENT_THRESHOLD_PX]?.coerceIn(
                TrackpadGestureEngine.MIN_TAP_MOVEMENT_THRESHOLD_PX,
                TrackpadGestureEngine.MAX_TAP_MOVEMENT_THRESHOLD_PX,
            ) ?: TrackpadGestureEngine.DEFAULT_TAP_MOVEMENT_THRESHOLD_PX,
            tapCorrectionCount = preferences[TAP_CORRECTION_COUNT]?.coerceIn(0, 999) ?: 0,
            sPenPrecisionEnabled = preferences[SPEN_PRECISION_ENABLED] ?: true,
            dragLockEnabled = preferences[DRAG_LOCK_ENABLED] ?: false,
            labsEnabled = preferences[LABS_ENABLED] ?: false,
            airMouseEnabled = preferences[AIR_MOUSE_ENABLED] ?: false,
            airTouchEnabled = preferences[AIR_TOUCH_ENABLED] ?: false,
            backTapEnabled = preferences[BACK_TAP_ENABLED] ?: false,
            volumeKeysEnabled = preferences[VOLUME_KEYS_ENABLED] ?: false,
        )
    }

    suspend fun update(transform: (TrackpadSettings) -> TrackpadSettings) {
        context.trackpadDataStore.edit { preferences ->
            val current = TrackpadSettings(
                pointerSpeed = preferences[POINTER_SPEED]?.coerceIn(0.3f, 1.35f) ?: 0.72f,
                acceleration = preferences[ACCELERATION]?.coerceIn(0.5f, 1.75f) ?: 1.0f,
                scrollSpeed = preferences[SCROLL_SPEED]?.coerceIn(0.35f, 1.8f) ?: 1.0f,
                naturalScroll = preferences[NATURAL_SCROLL] ?: true,
                scrollRailEnabled = preferences[SCROLL_RAIL_ENABLED] ?: true,
                scrollRailInverted = preferences[SCROLL_RAIL_INVERTED] ?: false,
                railSide = preferences[RAIL_SIDE]?.let(::enumValueOrNull) ?: TrackpadRailSide.Right,
                rotation = preferences[ROTATION]?.let(::enumValueOrNull) ?: TrackpadRotation.Deg0,
                hapticsEnabled = preferences[HAPTICS_ENABLED] ?: true,
                pointerTraceEnabled = preferences[POINTER_TRACE_ENABLED] ?: false,
                quietModeEnabled = preferences[QUIET_MODE_ENABLED] ?: true,
                idleBlankTimeoutMillis = preferences[IDLE_BLANK_TIMEOUT_MS]?.coerceIn(30_000, 600_000) ?: 120_000,
                backgroundOpacity = preferences[BACKGROUND_OPACITY]?.coerceIn(0.05f, 0.72f) ?: 0.48f,
                clockStyle = preferences[CLOCK_STYLE]?.let(::enumValueOrNull) ?: TrackpadClockStyle.Stacked,
                floatingMenuLayout = preferences[FLOATING_MENU_LAYOUT]?.let(::enumValueOrNull) ?: TrackpadFloatingMenuLayout.Horizontal,
                doubleTapTimeoutMillis = preferences[DOUBLE_TAP_TIMEOUT_MS]?.takeUnless { it <= 420 }?.coerceIn(350, 900) ?: 620,
                tapMovementThresholdPx = preferences[TAP_MOVEMENT_THRESHOLD_PX]?.coerceIn(
                    TrackpadGestureEngine.MIN_TAP_MOVEMENT_THRESHOLD_PX,
                    TrackpadGestureEngine.MAX_TAP_MOVEMENT_THRESHOLD_PX,
                ) ?: TrackpadGestureEngine.DEFAULT_TAP_MOVEMENT_THRESHOLD_PX,
                tapCorrectionCount = preferences[TAP_CORRECTION_COUNT]?.coerceIn(0, 999) ?: 0,
                sPenPrecisionEnabled = preferences[SPEN_PRECISION_ENABLED] ?: true,
                dragLockEnabled = preferences[DRAG_LOCK_ENABLED] ?: false,
                labsEnabled = preferences[LABS_ENABLED] ?: false,
                airMouseEnabled = preferences[AIR_MOUSE_ENABLED] ?: false,
                airTouchEnabled = preferences[AIR_TOUCH_ENABLED] ?: false,
                backTapEnabled = preferences[BACK_TAP_ENABLED] ?: false,
                volumeKeysEnabled = preferences[VOLUME_KEYS_ENABLED] ?: false,
            )
            val next = transform(current)
            preferences[POINTER_SPEED] = next.pointerSpeed.coerceIn(0.3f, 1.35f)
            preferences[ACCELERATION] = next.acceleration.coerceIn(0.5f, 1.75f)
            preferences[SCROLL_SPEED] = next.scrollSpeed.coerceIn(0.35f, 1.8f)
            preferences[NATURAL_SCROLL] = next.naturalScroll
            preferences[SCROLL_RAIL_ENABLED] = next.scrollRailEnabled
            preferences[SCROLL_RAIL_INVERTED] = next.scrollRailInverted
            preferences[RAIL_SIDE] = next.railSide.name
            preferences[ROTATION] = next.rotation.name
            preferences[HAPTICS_ENABLED] = next.hapticsEnabled
            preferences[POINTER_TRACE_ENABLED] = next.pointerTraceEnabled
            preferences[QUIET_MODE_ENABLED] = next.quietModeEnabled
            preferences[IDLE_BLANK_TIMEOUT_MS] = next.idleBlankTimeoutMillis.coerceIn(30_000, 600_000)
            preferences[BACKGROUND_OPACITY] = next.backgroundOpacity.coerceIn(0.05f, 0.72f)
            preferences[CLOCK_STYLE] = next.clockStyle.name
            preferences[FLOATING_MENU_LAYOUT] = next.floatingMenuLayout.name
            preferences[DOUBLE_TAP_TIMEOUT_MS] = next.doubleTapTimeoutMillis.coerceIn(350, 900)
            preferences[TAP_MOVEMENT_THRESHOLD_PX] = next.tapMovementThresholdPx.coerceIn(
                TrackpadGestureEngine.MIN_TAP_MOVEMENT_THRESHOLD_PX,
                TrackpadGestureEngine.MAX_TAP_MOVEMENT_THRESHOLD_PX,
            )
            preferences[TAP_CORRECTION_COUNT] = next.tapCorrectionCount.coerceIn(0, 999)
            preferences[SPEN_PRECISION_ENABLED] = next.sPenPrecisionEnabled
            preferences[DRAG_LOCK_ENABLED] = next.dragLockEnabled
            preferences[LABS_ENABLED] = next.labsEnabled
            preferences[AIR_MOUSE_ENABLED] = next.airMouseEnabled
            preferences[AIR_TOUCH_ENABLED] = next.airTouchEnabled
            preferences[BACK_TAP_ENABLED] = next.backTapEnabled
            preferences[VOLUME_KEYS_ENABLED] = next.volumeKeysEnabled
        }
    }

    suspend fun reset() {
        context.trackpadDataStore.edit { it.clear() }
    }

    private companion object {
        val POINTER_SPEED = floatPreferencesKey("pointer_speed")
        val ACCELERATION = floatPreferencesKey("acceleration")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        val NATURAL_SCROLL = booleanPreferencesKey("natural_scroll")
        val SCROLL_RAIL_ENABLED = booleanPreferencesKey("scroll_rail_enabled")
        val SCROLL_RAIL_INVERTED = booleanPreferencesKey("scroll_rail_inverted")
        val RAIL_SIDE = stringPreferencesKey("rail_side")
        val ROTATION = stringPreferencesKey("rotation")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val POINTER_TRACE_ENABLED = booleanPreferencesKey("pointer_trace_enabled")
        val QUIET_MODE_ENABLED = booleanPreferencesKey("quiet_mode_enabled")
        val IDLE_BLANK_TIMEOUT_MS = intPreferencesKey("idle_blank_timeout_ms")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val FLOATING_MENU_LAYOUT = stringPreferencesKey("floating_menu_layout")
        val DOUBLE_TAP_TIMEOUT_MS = intPreferencesKey("double_tap_timeout_ms")
        val TAP_MOVEMENT_THRESHOLD_PX = floatPreferencesKey("tap_movement_threshold_px")
        val TAP_CORRECTION_COUNT = intPreferencesKey("tap_correction_count")
        val SPEN_PRECISION_ENABLED = booleanPreferencesKey("spen_precision_enabled")
        val DRAG_LOCK_ENABLED = booleanPreferencesKey("drag_lock_enabled")
        val LABS_ENABLED = booleanPreferencesKey("labs_enabled")
        val AIR_MOUSE_ENABLED = booleanPreferencesKey("air_mouse_enabled")
        val AIR_TOUCH_ENABLED = booleanPreferencesKey("air_touch_enabled")
        val BACK_TAP_ENABLED = booleanPreferencesKey("back_tap_enabled")
        val VOLUME_KEYS_ENABLED = booleanPreferencesKey("volume_keys_enabled")
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }
